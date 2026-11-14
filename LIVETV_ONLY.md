# Nuvio + IPTV — solo Live TV

Rama `livetv-only`: la app quedó reducida a la parte de IPTV de Nuvio + IPTV.

## Qué queda
- Guía de canales (M3U, Xtream Codes, portales Stalker/MAG), EPG XMLTV, catch-up
- Reproductor de Live TV (Media3/ExoPlayer), pantalla completa, multivista
- Grabaciones (programadas y en segundo plano)
- Deportes (marcadores, horarios y qué canales pasan cada partido)
- Búsqueda de canales y programas (pantalla nueva, `livetv/only/LiveTvSearchScreen.kt`)
- Ajustes de Live TV, control parental, filas en el launcher de Android TV, copia en Google Drive
- Menú lateral nuevo y simple en `MainActivity.kt` (Live TV, Buscar, Deportes, Grabaciones, Multivista, Ajustes)

## Qué se quitó
- Todo Nuvio: home, catálogos, addons, detalles, su reproductor, debrid, torrents, Trakt/Simkl/MDBList,
  Jellyfin/Emby, watch party, perfiles con cuenta, plugins (CloudStream), actualizador
- On Demand (películas/series de Xtream) y Calendario: quedan stubs mínimos
  (`livetv/ondemand/OnDemandStub.kt`, `livetv/calendar/CalendarLiveBell.kt`) para no tocar la pantalla de ajustes
- Sincronización con cuenta Nuvio (Supabase): desactivada en `LiveTvCloudSync.kt`
- Sentry, baseline profiles, flavors `full`/`playstore`, mpv, libass, libtorrent, DV7
- Tests de Nuvio

`domain/repository/AddonRepository` y `CatalogRepository` se mantienen con implementaciones vacías
(`livetv/only/LiveTvOnlyModule.kt`), porque la guía y Deportes los consultan para pósters y streams.

## Compilar
`./gradlew :app:assembleDebug` (o el workflow `.github/workflows/build-livetv.yml`).
App ID: `com.nuvio.livetv`.
