#include "libretro.h"
#include <android/log.h>
#include <atomic>
#include <cstdarg>
#include <mutex>
#include <stdio.h>
#include <string>
#include <unordered_map>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "PhoenixLibretro", __VA_ARGS__)

#include "verificador_ambiente.c"

void phoenix_ra_definir_mapa(const struct retro_memory_map *mapa);

std::string g_system_dir;
std::string g_saves_dir;
std::atomic<int> g_formato_pixel{-1};

// Core options & overscan
std::mutex g_opcoes_mtx;
std::unordered_map<std::string, std::string> g_opcoes_core;
std::atomic<bool> g_opcoes_atualizadas{false};
std::atomic<bool> g_overscan{false};

// Dinamico: SET_SYSTEM_AV_INFO / SET_GEOMETRY
std::atomic<double> g_av_fps{0.0};
std::atomic<double> g_av_sample_rate{0.0};
std::atomic<float> g_av_aspect_ratio{0.0f};
std::atomic<int> g_av_base_width{0};
std::atomic<int> g_av_base_height{0};
std::atomic<bool> g_av_dirty{false};

extern std::atomic<int> g_ff_velocidade;

void phoenix_ambiente_limpar_estado() {
    {
        std::lock_guard<std::mutex> l(g_opcoes_mtx);
        g_opcoes_core.clear();
    }
    g_opcoes_atualizadas.store(false, std::memory_order_relaxed);
    g_av_fps.store(0.0, std::memory_order_relaxed);
    g_av_sample_rate.store(0.0, std::memory_order_relaxed);
    g_av_aspect_ratio.store(0.0f, std::memory_order_relaxed);
    g_av_base_width.store(0, std::memory_order_relaxed);
    g_av_base_height.store(0, std::memory_order_relaxed);
    g_av_dirty.store(false, std::memory_order_relaxed);
}

void phoenix_ambiente_definir_opcao(const char *chave, const char *valor) {
    if (!chave || !valor) return;
    std::lock_guard<std::mutex> l(g_opcoes_mtx);
    g_opcoes_core[chave] = valor;
    g_opcoes_atualizadas.store(true, std::memory_order_release);
}

void phoenix_ambiente_definir_overscan(bool ativo) {
    g_overscan.store(ativo, std::memory_order_relaxed);
}

static void registrar_opcao_padrao(const char *chave, const char *valor) {
    if (!chave || !valor || !chave[0]) return;
    std::lock_guard<std::mutex> l(g_opcoes_mtx);
    if (g_opcoes_core.find(chave) == g_opcoes_core.end()) {
        g_opcoes_core.emplace(chave, valor);
    }
}

static void retro_log_printf(enum retro_log_level level, const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    char buf[4096];
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);

    int priority = ANDROID_LOG_INFO;
    switch(level) {
        case RETRO_LOG_DEBUG: priority = ANDROID_LOG_DEBUG; break;
        case RETRO_LOG_INFO: priority = ANDROID_LOG_INFO; break;
        case RETRO_LOG_WARN: priority = ANDROID_LOG_WARN; break;
        case RETRO_LOG_ERROR: priority = ANDROID_LOG_ERROR; break;
        default: priority = ANDROID_LOG_INFO; break;
    }
    __android_log_print(priority, "PhoenixLibretro", "%s", buf);
}

bool cb_environment(unsigned cmd, void *data) {
    unsigned clean_cmd = cmd & 0xFFFF;
    switch (clean_cmd) {
        case 2: // RETRO_ENVIRONMENT_GET_OVERSCAN
            if (data) *(bool*)data = g_overscan.load(std::memory_order_relaxed);
            return true;
        case 3: // RETRO_ENVIRONMENT_GET_CAN_DUPE
            if (data) *(bool*)data = true;
            return true;
        case 9: // RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY
            if (data) *(const char**)data = g_system_dir.c_str();
            return true;
        case 10: { // RETRO_ENVIRONMENT_SET_PIXEL_FORMAT
            if (!data) return false;
            int format = *(const int*)data;
            if (format == 1 || format == 2) {
                g_formato_pixel.store(format, std::memory_order_relaxed);
                LOGI("SET_PIXEL_FORMAT aceito: %d", format);
                return true;
            }
            return false;
        }
        case 11: // RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS
            return true;
        case 15: { // RETRO_ENVIRONMENT_GET_VARIABLE
            if (!data) return false;
            auto *var = static_cast<struct retro_variable *>(data);
            if (!var->key) return false;
            std::lock_guard<std::mutex> l(g_opcoes_mtx);
            auto it = g_opcoes_core.find(var->key);
            if (it != g_opcoes_core.end()) {
                var->value = it->second.c_str();
                return true;
            }
            var->value = nullptr;
            return false;
        }
        case 16: { // RETRO_ENVIRONMENT_SET_VARIABLES
            if (!data) return true;
            const auto *vars = static_cast<const struct retro_variable *>(data);
            for (; vars->key && vars->value; ++vars) {
                std::string desc(vars->value);
                auto sep = desc.find("; ");
                if (sep != std::string::npos) {
                    std::string opcoes = desc.substr(sep + 2);
                    auto pipe = opcoes.find('|');
                    std::string padrao = (pipe != std::string::npos) ? opcoes.substr(0, pipe) : opcoes;
                    registrar_opcao_padrao(vars->key, padrao.c_str());
                }
            }
            return true;
        }
        case 17: // RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE
            if (data) *(bool*)data = g_opcoes_atualizadas.exchange(false, std::memory_order_acquire);
            return true;
        case 18: // RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME
            return true;
        case 27: { // RETRO_ENVIRONMENT_GET_LOG_INTERFACE
            if (!data) return false;
            struct retro_log_callback *cb = (struct retro_log_callback*)data;
            cb->log = retro_log_printf;
            return true;
        }
        case 31: // RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY
            if (data) *(const char**)data = g_saves_dir.c_str();
            return true;
        case 32: { // RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO
            if (!data) return false;
            const auto *av = static_cast<const struct retro_system_av_info *>(data);
            if (av->timing.fps > 0.0) g_av_fps.store(av->timing.fps, std::memory_order_relaxed);
            if (av->timing.sample_rate > 0.0) g_av_sample_rate.store(av->timing.sample_rate, std::memory_order_relaxed);
            if (av->geometry.base_width > 0) g_av_base_width.store(static_cast<int>(av->geometry.base_width), std::memory_order_relaxed);
            if (av->geometry.base_height > 0) g_av_base_height.store(static_cast<int>(av->geometry.base_height), std::memory_order_relaxed);
            if (av->geometry.aspect_ratio > 0.0f) {
                g_av_aspect_ratio.store(av->geometry.aspect_ratio, std::memory_order_relaxed);
            } else if (av->geometry.base_width > 0 && av->geometry.base_height > 0) {
                g_av_aspect_ratio.store(static_cast<float>(av->geometry.base_width) / static_cast<float>(av->geometry.base_height), std::memory_order_relaxed);
            }
            g_av_dirty.store(true, std::memory_order_release);
            LOGI("SET_SYSTEM_AV_INFO: %ux%u fps=%.2f sr=%.0f", av->geometry.base_width, av->geometry.base_height, av->timing.fps, av->timing.sample_rate);
            return true;
        }
        case 35: // RETRO_ENVIRONMENT_SET_CONTROLLER_INFO
            return true;
        case 36: // RETRO_ENVIRONMENT_SET_MEMORY_MAPS
            phoenix_ra_definir_mapa(static_cast<const struct retro_memory_map *>(data));
            return true;
        case 37: { // RETRO_ENVIRONMENT_SET_GEOMETRY
            if (!data) return false;
            const auto *geo = static_cast<const struct retro_game_geometry *>(data);
            if (geo->base_width > 0) g_av_base_width.store(static_cast<int>(geo->base_width), std::memory_order_relaxed);
            if (geo->base_height > 0) g_av_base_height.store(static_cast<int>(geo->base_height), std::memory_order_relaxed);
            if (geo->aspect_ratio > 0.0f) {
                g_av_aspect_ratio.store(geo->aspect_ratio, std::memory_order_relaxed);
            } else if (geo->base_width > 0 && geo->base_height > 0) {
                g_av_aspect_ratio.store(static_cast<float>(geo->base_width) / static_cast<float>(geo->base_height), std::memory_order_relaxed);
            }
            LOGI("SET_GEOMETRY: %ux%u aspect=%.3f", geo->base_width, geo->base_height, geo->aspect_ratio);
            return true;
        }
        case 49: // RETRO_ENVIRONMENT_GET_FASTFORWARDING
            if (data) *(bool*)data = (g_ff_velocidade.load(std::memory_order_relaxed) > 1);
            return true;
        case 51: // RETRO_ENVIRONMENT_GET_INPUT_BITMASKS
            if (data) *(bool*)data = true;
            return true;
        case 52: // RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION
            if (data) *(unsigned*)data = 2;
            return true;
        case 53: { // RETRO_ENVIRONMENT_SET_CORE_OPTIONS
            if (!data) return true;
            const auto *defs = static_cast<const struct retro_core_option_definition *>(data);
            for (; defs->key != nullptr; ++defs) {
                const char *val = defs->default_value ? defs->default_value : defs->values[0].value;
                registrar_opcao_padrao(defs->key, val);
            }
            return true;
        }
        case 55: // RETRO_ENVIRONMENT_SET_CORE_OPTIONS_DISPLAY
            return true;
        case 67: { // RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2
            if (!data) return true;
            const auto *v2 = static_cast<const struct retro_core_options_v2 *>(data);
            if (v2->definitions) {
                for (const auto *defs = v2->definitions; defs->key != nullptr; ++defs) {
                    const char *val = defs->default_value ? defs->default_value : defs->values[0].value;
                    registrar_opcao_padrao(defs->key, val);
                }
            }
            return true;
        }
        default:
            registrar_comando_nao_tratado(clean_cmd);
            return false;
    }
}