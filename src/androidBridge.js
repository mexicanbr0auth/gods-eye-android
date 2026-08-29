/**
 * Android bridge shim — roda o mais cedo possível (importado em main.js).
 * Sincroniza chaves vindas do app nativo (SharedPreferences via JS injection)
 * com o que o web espera: window.__GOOGLE_MAPS_API_KEY__, localStorage e
 * Cesium tokens.
 *
 * No Android, o MainActivity injeta via evaluateJavascript:
 *   localStorage.setItem('GOOGLE_MAPS_API_KEY', '...')
 *   window.__ANDROID_GOOGLE_KEY__ = '...'
 *   window.Android.getGoogleMapsKey()
 *
 * Este módulo tenta ler de várias fontes em ordem de prioridade.
 */

export function resolveAndroidKeys() {
  const sources = []

  // 1. Injeção direta do WebView (MainActivity AppConfig.toJsInjection)
  if (typeof window !== 'undefined') {
    if (window.__ANDROID_GOOGLE_KEY__) sources.push(window.__ANDROID_GOOGLE_KEY__)
    if (window.GOOGLE_MAPS_API_KEY) sources.push(window.GOOGLE_MAPS_API_KEY)
  }

  // 2. localStorage (persistido pelo AppConfig)
  try {
    const lsGoogle = localStorage.getItem('GOOGLE_MAPS_API_KEY')
    if (lsGoogle) sources.push(lsGoogle)
    const lsCesium = localStorage.getItem('CESIUM_ION_TOKEN')
    if (lsCesium) {
      if (typeof window !== 'undefined') window.__CESIUM_FROM_STORAGE__ = lsCesium
    }
  } catch {}

  // 3. Bridge síncrono Android (se disponível)
  try {
    if (typeof window !== 'undefined' && window.Android && typeof window.Android.getGoogleMapsKey === 'function') {
      const v = window.Android.getGoogleMapsKey()
      if (v) sources.push(v)
    }
  } catch {}

  // 4. Fallback para vite baked env (será usado se nada acima)
  return {
    google: sources.find(Boolean) || null,
    cesium: (() => {
      try {
        if (window?.__ANDROID_CESIUM_KEY__) return window.__ANDROID_CESIUM_KEY__
        if (window?.CESIUM_ION_TOKEN) return window.CESIUM_ION_TOKEN
        const ls = localStorage.getItem('CESIUM_ION_TOKEN')
        if (ls) return ls
      } catch {}
      return null
    })(),
  }
}

export function getGoogleMapsKeyWithFallback(viteKey) {
  const { google } = resolveAndroidKeys()
  return google || viteKey || null
}

export function getCesiumTokenWithFallback(viteToken) {
  const { cesium } = resolveAndroidKeys()
  return cesium || viteToken || null
}

// Auto-injeta no DOM para debug
if (typeof window !== 'undefined') {
  window.__ANDROID_BRIDGE_READY__ = true
  // Escuta evento disparado pelo MainActivity após permissão de localização
  window.addEventListener('android:location', (e) => {
    console.log('[androidBridge] location granted:', e.detail)
  })
}
