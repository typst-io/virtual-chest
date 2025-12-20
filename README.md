# VirtualChest

A Bukkit plugin that provides virtual chests for players, specialized for multi-instance environments using a lease-based distributed lock with a heartbeat.

## Changelogs

### 2.1.1
- fix: error log on closing server

### 2.1.0
- feat(api): OpenChestContext for customTitle/onClose

### 2.0.1
- fix: the case using '%' character in mysql id/pw using url encoder

### 2.0.0
- fix!: race condition on openChest using a lease-based lock with heartbeat.
- fix: set username/password via HikariConfig to prevent malformed JDBC URLs when password contains '%' by @SkyAsa2256
- feat(config): add message `alreadyOpenedChestMessage`

## Commands

- `/chest <chest-num>`: Opens your chest with the given number.  
  Requires `virtualchest.chest.<chestnum>` for chests beyond the first.  
  The first chest is allowed by default; to disable it, explicitly deny `virtualchest.chest.1`.

- `/chest open <player> <chest-num>`: Opens the specified player's chest.  
  Requires `virtualchest.op`.

- `/chest migration <datatype>`: Migrates data from the type set in the config to the given type (`sqlite`, `mysql`).  
  To switch the data source afterward, change `dbProtocol` in the config.

## Config

The `config.yml` is reloaded when you save the file.

- `dbProtocol`: Data source used by the plugin. Available: `sqlite`, `mysql`
- `dbHost`: MySQL host
- `dbPort`: MySQL port
- `dbUsername`: MySQL username
- `dbPassword`: MySQL password (defaults to system env `MYSQL_PW`)
- `chestTitle`: Title of the chest inventory
- `chestSizeRow`: Number of rows in the chest inventory
- `noPermissionMessage`: Message shown when using `/chest <chest-num>` without permission
- `alreadyOpenedChestMessage`: Message shown when the chest was locked
- `overrideLocale`: Override locale — `ko_kr`, `en_us`

## API

### Events

- `dev.entree.vchest.api.ChestOpenEvent`: Fires when a player tries to open a chest.
