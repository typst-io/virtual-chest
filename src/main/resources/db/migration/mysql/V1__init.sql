CREATE TABLE IF NOT EXISTS player
(
    player_id INTEGER NOT NULL AUTO_INCREMENT,
    player_uuid VARCHAR(36) NOT NULL,
    player_name VARCHAR(16) NOT NULL,
    PRIMARY KEY (player_id),
    UNIQUE(player_uuid)
);

CREATE TABLE IF NOT EXISTS chest
(
    chest_num INTEGER NOT NULL,
    chest_player_id INTEGER NOT NULL,
    FOREIGN KEY (chest_player_id)
            REFERENCES player(player_id)
            ON DELETE CASCADE,
    PRIMARY KEY (chest_player_id, chest_num)
);

CREATE TABLE IF NOT EXISTS slot
(
    slot_slot INTEGER NOT NULL,
    slot_chest_num INTEGER NOT NULL,
    slot_player_id INTEGER NOT NULL,
    slot_item_bytes MEDIUMBLOB NOT NULL,
    FOREIGN KEY (slot_player_id, slot_chest_num)
            REFERENCES chest(chest_player_id, chest_num)
            ON DELETE CASCADE,
    PRIMARY KEY (slot_slot, slot_chest_num, slot_player_id)
);
