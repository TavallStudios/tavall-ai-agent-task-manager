package org.tavall.ai.app.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
public class SpringJacksonMcpJsonMapper implements McpJsonMapper {

  private final ObjectMapper objectMapper;

  public SpringJacksonMcpJsonMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public <T> T readValue(String content, Class<T> clazz) throws IOException {
    return objectMapper.readValue(content, clazz);
  }

  @Override
  public <T> T readValue(byte[] content, Class<T> clazz) throws IOException {
    return objectMapper.readValue(content, clazz);
  }

  @Override
  public <T> T readValue(String content, TypeRef<T> typeRef) throws IOException {
    return objectMapper.readValue(content, objectMapper.getTypeFactory().constructType(typeRef.getType()));
  }

  @Override
  public <T> T readValue(byte[] content, TypeRef<T> typeRef) throws IOException {
    return objectMapper.readValue(content, objectMapper.getTypeFactory().constructType(typeRef.getType()));
  }

  @Override
  public <T> T convertValue(Object source, Class<T> clazz) {
    return objectMapper.convertValue(source, clazz);
  }

  @Override
  public <T> T convertValue(Object source, TypeRef<T> typeRef) {
    return objectMapper.convertValue(source, objectMapper.getTypeFactory().constructType(typeRef.getType()));
  }

  @Override
  public String writeValueAsString(Object value) throws IOException {
    return objectMapper.writeValueAsString(value);
  }

  @Override
  public byte[] writeValueAsBytes(Object value) throws IOException {
    return objectMapper.writeValueAsBytes(value);
  }
}

