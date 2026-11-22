/* MusicFree plugin runtime for QuickJS.
 * Loaded after libs.js, which defines globalThis.__mfLibs.
 * Kotlin provides the async __http(json) and sync __log(text) bindings.
 */
(function () {
    "use strict";

    // ---- tiny Web API polyfills -------------------------------------------
    if (typeof globalThis.setTimeout === "undefined") {
        globalThis.setTimeout = function (fn) {
            Promise.resolve().then(fn);
            return 0;
        };
        globalThis.clearTimeout = function () {};
        globalThis.setInterval = function () { return 0; };
        globalThis.clearInterval = function () {};
    }
    if (typeof globalThis.queueMicrotask === "undefined") {
        globalThis.queueMicrotask = function (fn) { Promise.resolve().then(fn); };
    }

    var B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    if (typeof globalThis.atob === "undefined") {
        globalThis.atob = function (input) {
            var s = String(input).replace(/[\t\n\f\r ]/g, "").replace(/=+$/, "");
            var out = "";
            var bits = 0, acc = 0;
            for (var i = 0; i < s.length; i++) {
                var v = B64.indexOf(s.charAt(i));
                if (v < 0) continue;
                acc = (acc << 6) | v;
                bits += 6;
                if (bits >= 8) {
                    bits -= 8;
                    out += String.fromCharCode((acc >> bits) & 0xff);
                }
            }
            return out;
        };
    }
    if (typeof globalThis.btoa === "undefined") {
        globalThis.btoa = function (input) {
            var s = String(input);
            var out = "";
            for (var i = 0; i < s.length; i += 3) {
                var a = s.charCodeAt(i) & 0xff;
                var b = i + 1 < s.length ? s.charCodeAt(i + 1) & 0xff : NaN;
                var c = i + 2 < s.length ? s.charCodeAt(i + 2) & 0xff : NaN;
                out += B64.charAt(a >> 2);
                out += B64.charAt(((a & 3) << 4) | (isNaN(b) ? 0 : b >> 4));
                out += isNaN(b) ? "=" : B64.charAt(((b & 15) << 2) | (isNaN(c) ? 0 : c >> 6));
                out += isNaN(c) ? "=" : B64.charAt(c & 63);
            }
            return out;
        };
    }
    if (typeof globalThis.TextDecoder === "undefined") {
        globalThis.TextDecoder = function (encoding) {
            this.encoding = (encoding || "utf-8").toLowerCase();
        };
        globalThis.TextDecoder.prototype.decode = function (buffer) {
            var bytes = buffer instanceof ArrayBuffer ? new Uint8Array(buffer) : buffer;
            if (!bytes) return "";
            var out = "";
            for (var i = 0; i < bytes.length;) {
                var b = bytes[i++];
                if (b < 0x80) {
                    out += String.fromCharCode(b);
                } else if (b < 0xe0) {
                    out += String.fromCharCode(((b & 0x1f) << 6) | (bytes[i++] & 0x3f));
                } else if (b < 0xf0) {
                    out += String.fromCharCode(((b & 0x0f) << 12) | ((bytes[i++] & 0x3f) << 6) | (bytes[i++] & 0x3f));
                } else {
                    var cp = ((b & 0x07) << 18) | ((bytes[i++] & 0x3f) << 12) |
                        ((bytes[i++] & 0x3f) << 6) | (bytes[i++] & 0x3f);
                    cp -= 0x10000;
                    out += String.fromCharCode(0xd800 + (cp >> 10), 0xdc00 + (cp & 0x3ff));
                }
            }
            return out;
        };
    }

    // ---- console ----------------------------------------------------------
    function stringifyArg(v) {
        if (typeof v === "string") return v;
        try { return JSON.stringify(v); } catch (e) { return String(v); }
    }
    globalThis.console = {
        log: function () { __log(Array.prototype.map.call(arguments, stringifyArg).join(" ")); },
        info: function () { __log(Array.prototype.map.call(arguments, stringifyArg).join(" ")); },
        warn: function () { __log("[warn] " + Array.prototype.map.call(arguments, stringifyArg).join(" ")); },
        error: function () { __log("[error] " + Array.prototype.map.call(arguments, stringifyArg).join(" ")); },
        debug: function () {},
    };

    // ---- module registry --------------------------------------------------
    function withDefault(pkg) {
        if (!pkg || (typeof pkg !== "object" && typeof pkg !== "function")) return pkg;
        var proxy = new Proxy(pkg, {
            get: function (target, key) {
                if (key === "default") return proxy;
                if (key === "__esModule") return true;
                return Reflect.get(target, key);
            },
        });
        return proxy;
    }

    var moduleCache = {};
    globalThis.__mfRequire = function (name) {
        if (name === "axios") return globalThis.axios;
        if (moduleCache[name]) return moduleCache[name];
        var pkg = __mfLibs[name];
        if (!pkg) throw new Error("MusicFree 插件引用了不支持的模块：" + name);
        var wrapped = withDefault(pkg);
        moduleCache[name] = wrapped;
        return wrapped;
    };

    // ---- axios-compatible HTTP client ------------------------------------
    var axios = (function () {
        function encode(v) {
            return encodeURIComponent(v)
                .replace(/%20/g, "+")
                .replace(/[!'()*]/g, function (c) { return "%" + c.charCodeAt(0).toString(16).toUpperCase(); });
        }
        function appendParam(parts, key, value) {
            if (value === null || value === undefined) return;
            if (Array.isArray(value)) {
                for (var i = 0; i < value.length; i++) appendParam(parts, key + "[]", value[i]);
            } else if (typeof value === "object") {
                for (var k in value) {
                    if (Object.prototype.hasOwnProperty.call(value, k)) appendParam(parts, key + "[" + k + "]", value[k]);
                }
            } else {
                parts.push(encode(key) + "=" + encode(value));
            }
        }
        function serializeParams(params) {
            if (!params) return "";
            if (typeof params === "string") return params;
            var parts = [];
            for (var key in params) {
                if (Object.prototype.hasOwnProperty.call(params, key)) appendParam(parts, key, params[key]);
            }
            return parts.join("&");
        }
        function normalizeHeaders(headers) {
            var out = {};
            if (!headers) return out;
            if (typeof headers.toJSON === "function") headers = headers.toJSON();
            for (var key in headers) {
                if (Object.prototype.hasOwnProperty.call(headers, key) && headers[key] !== undefined && headers[key] !== null) {
                    out[key] = String(headers[key]);
                }
            }
            return out;
        }
        function headerValue(headers, name) {
            for (var k in headers) {
                if (k.toLowerCase() === name.toLowerCase()) return headers[k];
            }
            return undefined;
        }
        function decodeBase64(b64) {
            var bin = atob(b64 || "");
            var bytes = new Uint8Array(bin.length);
            for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
            return bytes.buffer;
        }
        function createError(message, config, response) {
            var err = new Error(message);
            err.config = config;
            err.response = response;
            err.isAxiosError = true;
            return err;
        }
        function create(defaults) {
            defaults = defaults || {};
            var instance = async function (configOrUrl, maybeConfig) {
                var config = typeof configOrUrl === "string"
                    ? Object.assign({}, maybeConfig || {}, { url: configOrUrl })
                    : (configOrUrl || {});
                var merged = Object.assign({}, defaults, config);
                merged.headers = Object.assign({}, defaults.headers || {}, config.headers || {});
                return request(merged);
            };
            instance.request = instance;
            instance.get = function (url, config) { return instance(Object.assign({}, config || {}, { url: url, method: "get" })); };
            instance.delete = function (url, config) { return instance(Object.assign({}, config || {}, { url: url, method: "delete" })); };
            instance.head = function (url, config) { return instance(Object.assign({}, config || {}, { url: url, method: "head" })); };
            instance.options = function (url, config) { return instance(Object.assign({}, config || {}, { url: url, method: "options" })); };
            instance.post = function (url, data, config) { return instance(Object.assign({}, config || {}, { url: url, method: "post", data: data })); };
            instance.put = function (url, data, config) { return instance(Object.assign({}, config || {}, { url: url, method: "put", data: data })); };
            instance.patch = function (url, data, config) { return instance(Object.assign({}, config || {}, { url: url, method: "patch", data: data })); };
            instance.create = function (config) {
                var next = Object.assign({}, defaults, config || {});
                next.headers = Object.assign({}, defaults.headers || {}, (config && config.headers) || {});
                return create(next);
            };
            instance.defaults = defaults;
            instance.interceptors = {
                request: { use: function () {}, eject: function () {} },
                response: { use: function () {}, eject: function () {} },
            };
            instance.all = function (promises) { return Promise.all(promises); };
            instance.spread = function (fn) { return function (arr) { return fn.apply(null, arr); }; };
            return instance;
        }
        async function request(config) {
            var url = config.url || "";
            if (config.baseURL && !/^[a-z][a-z0-9+.-]*:/i.test(url)) {
                url = String(config.baseURL).replace(/\/+$/, "") + "/" + url.replace(/^\/+/, "");
            }
            var query = serializeParams(config.params);
            if (query) url += (url.indexOf("?") >= 0 ? "&" : "?") + query;

            var headers = normalizeHeaders(config.headers);
            var data = config.data;
            var contentType = headerValue(headers, "content-type");
            if (data !== undefined && data !== null && typeof data !== "string") {
                if (!contentType && data instanceof ArrayBuffer) {
                    data = btoa(String.fromCharCode.apply(null, new Uint8Array(data)));
                } else if (!contentType || /json/i.test(contentType)) {
                    data = JSON.stringify(data);
                    if (!contentType) headers["Content-Type"] = "application/json";
                } else if (/x-www-form-urlencoded/i.test(contentType)) {
                    data = serializeParams(data);
                } else {
                    data = String(data);
                }
            }

            var requestJson = JSON.stringify({
                url: url,
                method: (config.method || "get").toUpperCase(),
                headers: headers,
                body: data === undefined || data === null ? "" : String(data),
                timeoutMs: config.timeout || 20000,
                binary: config.responseType === "arraybuffer",
            });

            var raw = await __http(requestJson);
            var res = JSON.parse(raw);
            if (res.error) throw createError(res.error, config, null);

            var responseHeaders = res.headers || {};
            var response = {
                data: res.body,
                status: res.status,
                statusText: res.statusText || "",
                headers: responseHeaders,
                config: config,
                request: {},
            };
            var ct = headerValue(responseHeaders, "content-type");
            if (config.responseType === "arraybuffer") {
                response.data = decodeBase64(res.body || "");
            } else if (res.binary) {
                response.data = decodeBase64(res.body || "");
            } else if (/json/i.test(String(ct || ""))) {
                try { response.data = JSON.parse(res.body); } catch (e) { /* keep text */ }
            }
            var validate = config.validateStatus;
            var ok = validate ? !!validate(res.status) : res.status >= 200 && res.status < 300;
            if (!ok) {
                throw createError("Request failed with status code " + res.status, config, response);
            }
            return response;
        }
        var axios = create({});
        axios.default = axios;
        return axios;
    })();
    globalThis.axios = axios;

    // ---- plugin registry --------------------------------------------------
    globalThis.__mfPlugins = {};

    globalThis.__mfAdd = function (id, instance) {
        if (!instance || typeof instance !== "object") throw new Error("插件没有导出对象");
        if (!instance.platform) throw new Error("插件缺少 platform 字段");
        instance.__mfId = id;
        globalThis.__mfPlugins[instance.platform] = instance;
        var methods = ["search", "getMediaSource", "getMusicInfo", "getLyric", "getAlbumInfo",
            "getMusicSheetInfo", "getArtistWorks", "importMusicItem", "importMusicSheet",
            "getTopLists", "getTopListDetail"];
        var supported = [];
        for (var i = 0; i < methods.length; i++) {
            if (typeof instance[methods[i]] === "function") supported.push(methods[i]);
        }
        return JSON.stringify({
            platform: instance.platform,
            version: instance.version || "0.0.0",
            supported: supported,
        });
    };

    globalThis.__mfPlatforms = function () {
        var out = [];
        for (var key in __mfPlugins) {
            if (!Object.prototype.hasOwnProperty.call(__mfPlugins, key)) continue;
            var p = __mfPlugins[key];
            var supported = [];
            var methods = ["search", "getMediaSource", "getMusicInfo", "getLyric"];
            for (var i = 0; i < methods.length; i++) {
                if (typeof p[methods[i]] === "function") supported.push(methods[i]);
            }
            out.push({ platform: p.platform, version: p.version || "0.0.0", supported: supported });
        }
        return JSON.stringify(out);
    };

    globalThis.__mfSearch = async function (platform, query, page, type) {
        var p = __mfPlugins[platform];
        if (!p) throw new Error("插件未加载：" + platform);
        if (typeof p.search !== "function") throw new Error("插件不支持搜索：" + platform);
        var result = await p.search(query, page || 1, type || "music");
        return JSON.stringify(result || { data: [], isEnd: true });
    };

    globalThis.__mfMedia = async function (platform, itemJson, quality) {
        var p = __mfPlugins[platform];
        if (!p) throw new Error("插件未加载：" + platform);
        var item = JSON.parse(itemJson);
        var result;
        if (typeof p.getMediaSource === "function") {
            result = await p.getMediaSource(item, quality || "standard");
        } else {
            result = { url: item.url };
        }
        return JSON.stringify(result || {});
    };

    globalThis.__mfLyric = async function (platform, itemJson) {
        var p = __mfPlugins[platform];
        if (!p) throw new Error("插件未加载：" + platform);
        var item = JSON.parse(itemJson);
        if (typeof p.getLyric !== "function") throw new Error("插件不支持歌词");
        var result = await p.getLyric(item);
        return JSON.stringify(result || {});
    };
})();
