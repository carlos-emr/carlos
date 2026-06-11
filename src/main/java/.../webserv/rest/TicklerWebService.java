// ...

// In completeTicklers() method
ArrayNode ticklerIds = (ArrayNode) json.get("ticklers");
for (JsonNode idNode : ticklerIds) {
    int ticklerNo = idNode.asInt();
    ticklerManager.completeTickler(getLoggedInInfo(), ticklerNo, ...);
}

// ...

// In deleteTicklers() method
ArrayNode ticklerIds = (ArrayNode) json.get("ticklers");
for (JsonNode idNode : ticklerIds) {
    int ticklerNo = idNode.asInt();
    ticklerManager.deleteTickler(getLoggedInInfo(), ticklerNo, ...);
}