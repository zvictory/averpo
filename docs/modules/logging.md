# Texnik логгинг (developer log'лари) - SPEC

Ҳолат: **БАЖАРИЛГАН** (DEC-099; спец тасдиғи: фойдаланувчи «logging specni tasdiqlayman»). Манба талаб: «ҳар бир амал logger'да
кўриниб туриши керак - warn, info, error ҳаммаси; logs папкада
сақлансин; жуда яхши бошқарилсин - келажакда муаммо топиш учун
developer'га tool».

## Чегара: аудит ≠ texnik log

- **Аудит журнали** (мавжуд, audit модули): фойдаланувчи АМАЛЛАРИ
  базада, UI'да кўринади - бизнес далил (ким нима қилди).
- **Texnik log** (шу spec): developer учун файлга ёзилади - муаммо
  ташхиси (стектрейс, давомийлик, контекст). Иккиси бир-бирини
  АЛМАШТИРМАЙДИ, иккисига ҳам ёзиладиган амаллар бор.

## Бошланғич ҳолат (099 гача, тарихий; текширув натижаси)

099 да қурилди: logback-spring.xml + logs/ (app.log + error.log,
rotation билан) + MDC + амаллар изи; жонлида
LOG_DIR=/var/lib/averpo/logs. Қуйидаги расм 099 гача бўлган ҳолат:

- Logger атиги 7 файлда; logback конфиг ЙЎҚ; application.yml'да
  logging секцияси ЙЎҚ; logs папка ЙЎҚ.
- BusinessRuleException GlobalExceptionHandler'да DEBUG - default
  INFO даражада БР радлари умуман кўринмайди.
- Серверда systemd → journald (journalctl -u averpo) - бор, лекин
  формат хом, файл бошқаруви/rotation биз томонда эмас.

## Дизайн

### 1) logback-spring.xml (янги, src/main/resources)

- **Console appender**: ҳозирги кўриниш сақланади (dev одатлари).
- **Асосий файл**: `logs/app.log` - SizeAndTimeBasedRollingPolicy:
  кунлик + 20MB бўлак, 30 кун тарих, умумий сиғим 500MB (эски
  ўз-ўзидан ўчади - «жуда яхши бошқарилади» талаби, logrotate
  КЕРАК ЭМАС).
- **Хато файли**: `logs/error.log` - ThresholdFilter WARN+ (тез
  триаж: муаммо борми - аввал шу файл очилади).
- Папка: default `./logs` (иш папкасига нисбатан); env override
  `LOG_DIR` (серверда APP_DIR/logs). `.gitignore`га `logs/`.
- Қатор намунаси (MDC билан):
  ` 18:42:03.512 INFO [a3f2c1] admin c.s.d.ledger.PostingService - JE POSTED: INVOICE INV-2026-00042 ...`

### 2) MDC контекст (ҳар қаторда КИМ ва ҚАЙСИ сўров)

- Янги OncePerRequestFilter (web пакети): ҳар сўровга қисқа
  request-id (`rid`, 6 белги) + аутентификацияланган `user` MDC'га;
  сўров охирида тозаланади. Шу икки майдон pattern'да - бир
  фойдаланувчи сессиясидаги барча қаторлар бир қарашда боғланади.

### 3) «Ҳар бир амал кўринсин» - марказий нуқталар (ҳар service'га қўлда ЭМАС)

- **HTTP қатлам** (ўша filter'да): ҳар ЁЗУВЧИ сўров (POST) INFO -
  метод, йўл, status, давомийлик ms. GET'лар DEBUG (шовқин
  бўлмасин; керакда даража кўтарилади).
- **PostingService**: ҳар JE post/reverse INFO - манба тури, ҳужжат
  рақами, home жами (молия юраги - ҳар проводка изли).
- **GlobalExceptionHandler**: BusinessRuleException DEBUG → **WARN**
  (БР ради - айнан «warn» синфи; код + хабар + йўл);
  кутилмаган хато ERROR (стектрейс билан, мавжуд);
  NotFound/NoResource DEBUG'лигича (шовқин).
- **Мавжуд 7 logger жойи** сақланади (startup, scheduler, factory
  reset, admin init).
- Аудитга ёзиладиган амаллар (login/logout/lockout) texnik log'да
  ҳам INFO/WARN из қолдиради (LoginAttemptListener'га WARN lockout).

### 4) Даражалар (application.yml)

```yaml
logging:
  level:
    root: INFO
  com.averpo.erp: INFO # керакда DEBUG (env орқали ҳам)
    org.hibernate: WARN
    org.springframework.security: WARN
```

Env override қолипи: `LOGGING_LEVEL_COM_AVERPO_ERP=DEBUG`
(Spring стандарт) - серверда рестарт билан даража алмашади.

### 5) Сервер

- systemd/journald ҳам қолаверади (stdout console appender орқали);
  файллар APP_DIR/logs да (unit'даги WorkingDirectory ёки LOG_DIR).
- deploy/README'га: log қаерда, error.log триажи, даража кўтариш.

## Қоидалар

- Log хабарлари кирилл ўзбек (изоҳ қоидаси билан бир хил), лекин
  қидириладиган идентификаторлар (ҳужжат рақами, BR код, URL) айнан.
- ҲЕЧ ҚАЧОН log'га: парол, токен, CSRF қиймати, тўлиқ карта/ҳисоб
  рақамлари. Фойдаланувчи киритган хом матн фақат DEBUG'да.
- Log ёзиш оқим мантиғини ЎЗГАРТИРМАЙДИ (фақат кузатув).

## 2-босқич (ҳозир ЭМАС)

- Actuator loggers endpoint (рестартсиз даража алмашиш); log viewer
  UI (админга); JSON форматли log (агар ташқи йиғувчи уланса);
  slow-query/SQL логи.
