package io.github.sefiraat.networks.network;

public enum NodeType {
    CONTROLLER,
    BRIDGE,
    STORAGE_MONITOR,
    IMPORT,
    EXPORT,
    GRID,
    CELL,
    WIPER,
    GRABBER,
    PUSHER,
    CUTTER,
    PASTER,
    VACUUM,
    PURGER,
    CRAFTER,
    POWER_NODE,
    POWER_OUTLET,
    POWER_DISPLAY,
    ENCODER,
    GREEDY_BLOCK,
    WIRELESS_TRANSMITTER,
    WIRELESS_RECEIVER,

    // from networks expansion
    LINE_TRANSMITTER,
    LINE_TRANSMITTER_PUSHER,
    LINE_TRANSMITTER_PUSHER_PLUS,
    LINE_TRANSMITTER_GRABBER,
    LINE_TRANSMITTER_GRABBER_PLUS,
    COORDINATE_TRANSMITTER,
    NE_COORDINATE_RECEIVER,
    NEA_GREEDY_BLOCK,
    NEA_IMPORT,
    NEA_EXPORT,

}
