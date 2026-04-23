# NoraVPN Architecture

## 1. Общая архитектура

### 1.1 Слои приложения

- `UI (Compose)`:
  - экраны и навигация: `app/src/main/java/com/privatevpn/app/ui/`, `.../ui/screens/`, `.../navigation/`
  - отображение статуса VPN, ошибок, профилей, настроек и split tunneling
- `ViewModel`:
  - `AppViewModel` координирует бизнес-операции, состояние UI, события логов, profile selection и connect/disconnect
- `Domain/Application orchestration`:
  - выбор backend, backend switch, обработка recoverable/non-recoverable ошибок, ретраи
- `Repositories / Data`:
  - `RoomProfilesRepository`, `DataStoreUserSettingsRepository`, `AndroidInstalledAppsRepository`
- `Backend adapters`:
  - `XrayBackendAdapter` (через `VpnController` + `PrivateVpnService`)
  - `AmneziaWgBackendAdapter` (через `GoBackend`)
- `VPN service / runtime`:
  - `PrivateVpnService` (Xray + tun2proxy data plane + TUN lifecycle)
  - `VpnRuntimeStateStore` (shared runtime state/status/error)
- `Parser/import`:
  - `ProfileImportParser`, `AmneziaWgConfigParser`
- `Logging`:
  - event log в `AppViewModel`
  - runtime-level диагностика в `PrivateVpnService` и backend adapters

### 1.2 Поддержка нескольких протоколов

- Профиль хранит `ProfileType` (`VLESS/VMESS/TROJAN/XRAY_JSON/XRAY_VLESS_REALITY/AMNEZIA_WG_20`).
- Выбор backend:
  - `AMNEZIA_WG_20` -> `AmneziaWgBackendAdapter`
  - остальные типы -> `XrayBackendAdapter`
- В `AppViewModel` используется stateful switch с учетом:
  - текущего активного backend
  - последнего backend (`lastBackendProfileType`)
  - текущего runtime status (`READY/CONNECTING/CONNECTED/...`)

### 1.3 Профили

- Профили импортируются через `ProfileImportParser`.
- Хранятся в Room.
- Активный профиль хранится в DataStore (`activeProfileId`).
- При подключении используется активный профиль или первый доступный.

### 1.4 Split tunneling / trusted apps

- Управляется `privateSessionEnabled` + `trustedPackages`.
- Для Xray применяется через `PrivateVpnService.Builder` (`addAllowedApplication` в режиме Private Session).
- Для AWG применяется через `AmneziaWgRuntimeConfigBuilder` (`IncludeApplications`).

### 1.5 SOCKS

- Пользовательский localhost SOCKS настраивается в settings.
- Для Xray data plane используется внутренний SOCKS (`Tun2ProxyDataPlane`).
- Для AWG backend пользовательский SOCKS не применяется автоматически runtime-ом (логируется в notes).

### 1.6 Notification / Quick Tile

- Foreground notification: `PrivateVpnService`.
- Quick tile: `VpnQuickSettingsTileService`.
- Ошибки tile теперь маппятся на кодированные `TILE-*`.

## 2. Жизненный цикл подключения

1. Пользователь выбирает профиль.
2. Нажимает `Подключить`.
3. `AppViewModel.connectVpn()`:
   - сериализует операцию через `backendOperationMutex`
   - валидирует permission/split/socks состояния
   - определяет целевой backend
4. Если нужен межпротокольный switch:
   - stop старого backend
   - ожидание готовности runtime
   - короткий settle delay
5. Старт целевого backend:
   - Xray: runtime config -> `VpnController` -> `PrivateVpnService`
   - AWG: `GoBackend.setState(UP, config)`
6. Статус -> `CONNECTED`, обновление tile/notification/UI.
7. Disconnect:
   - stop текущего backend
   - очищение runtime state
   - статус `READY` или `NO_PERMISSION`.

## 3. Backend Switch (Xray <-> AWG)

### 3.1 Предыдущая проблема

- Race при смене backend engine:
  - старый backend еще не полностью освобожден
  - новый backend стартует слишком рано
  - первый connect мог вернуть нативный `Unknown error`
  - второй connect срабатывал, когда teardown уже завершился

### 3.2 Что изменено

- Введен координируемый switch pipeline в `AppViewModel`:
  - `connectVpnInternal()`
  - `performBackendSwitch()`
  - `awaitBackendReadyStatus()`
  - `startBackendWithWarmupRetry()`
- Добавлены stage-логи backend switch:
  - старый тип backend
  - новый тип backend
  - этап остановки
  - этап ожидания готовности
  - факт готовности
- Добавлен авто-ретрай запуска после switch при transient warmup-ошибках.
- Добавлен `lastBackendProfileType`, чтобы switch корректно работал и после disconnect.

### 3.3 Защита от раннего connect

- Если connect приходит во время предыдущей backend-операции:
  - возвращается recoverable ошибка `BACKEND-001`
  - в лог пишется явная причина раннего вызова.

## 4. Система ошибок

- Введены `AppErrorCode`, `AppError`, `AppErrors`.
- В UI показывается короткое сообщение с кодом:
  - `Код ошибки: BACKEND-001. ...`
- В dev-логах сохраняется техническая причина:
  - домен, recoverable flag, raw reason.
- Ошибки разделены по доменам:
  - profile import
  - backend switch
  - xray runtime
  - awg runtime
  - socks/localhost
  - split tunneling
  - notification/tile
  - generic UI/state

Подробный каталог: `docs/error-codes.md`.
