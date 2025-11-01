# VirtualChest

A bukkit plugin to use virtual chests for players.

## Commands

- `/chest <chest-num>`: open the given number of a player's chest. requires perm `virtualchest.chest.<chestnum>` except first chest, if you want to disable first chest then you need to add permission node `virtualchest.chest.1` as false.
- `/chest open <player> <chest-num>`: open the given number of the given player's chest. requires perm `virtualchest.op`
- `/chest migration <datatype>` migration database from the datatype from config to the given datatype -- sqlite, mysql -- then if you want to change datasource, need to edit the `dbProtocol` in config

## Config

- `dbProtocol`: the datasource plugin use, available: sqlite, mysql
- `dbHost`: the db host for mysql
- `dbPort`: the db port for mysql
- `dbUsername` the db username for mysql
- `dbPassword` the db username for mysql, defaults system env `MYSQL_PW`
- `chestTitle`: the title of a chest inventory
- `chestSizeRow`: the row of a chest inventory
- `noPermissionMesssage`: the message when use `/chest <chestnum>`
- `overrideLocale`: override locale -- `ko_kr`, `en_us`

## API

### Events

- `dev.entree.vchest.api.ChestOpenEvent`: Fires when a player trying to open a chest.