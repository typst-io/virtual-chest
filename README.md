# VirtualChest

A Bukkit plugin that provides virtual chests for players.

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
- `overrideLocale`: Override locale — `ko_kr`, `en_us`

## API

### Events

- `dev.entree.vchest.api.ChestOpenEvent`: Fires when a player tries to open a chest.
