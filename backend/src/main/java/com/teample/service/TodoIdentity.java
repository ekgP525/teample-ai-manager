package com.teample.service;

import com.teample.entity.TodoData;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 회의록 업무 ID 부여 규칙. 옛 회의록(ID 없음)은 처음 만날 때 ID를 채워 넣는다. */
public final class TodoIdentity {

    private TodoIdentity() {
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /** 비어 있거나 중복된 ID를 새 값으로 채운다. 하나라도 바뀌면 true. */
    public static boolean assignMissingIds(List<TodoData> todos) {
        if (todos == null || todos.isEmpty()) {
            return false;
        }
        boolean changed = false;
        Set<String> seen = new HashSet<>();
        for (TodoData todo : todos) {
            if (todo == null) {
                continue;
            }
            String id = todo.getId();
            if (id == null || id.isBlank() || !seen.add(id.trim())) {
                String fresh = newId();
                todo.setId(fresh);
                seen.add(fresh);
                changed = true;
            } else if (!id.equals(id.trim())) {
                todo.setId(id.trim());
                changed = true;
            }
        }
        return changed;
    }
}
