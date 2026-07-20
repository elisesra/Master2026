package experiment.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class LowLevelRequirementSchema {

    private LowLevelRequirementSchema() {
    }

    static ObjectNode create(ObjectMapper objectMapper) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("type", "object");
        item.putArray("required").add("allocation").add("requirement");
        item.put("additionalProperties", false);
        item.putObject("properties").putObject("allocation").put("type", "string");
        item.withObject("properties").putObject("requirement").put("type", "string");

        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.putArray("required").add("low_level_requirements");
        schema.put("additionalProperties", false);
        schema.putObject("properties")
                .putObject("low_level_requirements")
                .put("type", "array")
                .put("minItems", 1)
                .set("items", item);
        return schema;
    }
}
