package com.example.workfloworchestrator.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionContext {
    private Map<String, Object> variables = new HashMap<>();

    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    public Object getVariable(String key) {
        return variables.get(key);
    }

    public <T> T getVariable(String key, Class<T> type) {
        Object value = variables.get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return type.cast(value);
        }
        return null;
    }

    public boolean hasVariable(String key) {
        return variables.containsKey(key);
    }

    public void removeVariable(String key) {
        variables.remove(key);
    }

    public void clearVariables() {
        variables.clear();
    }

    public Map<String, Object> getAllVariables() {
        return new HashMap<>(variables);
    }
}
