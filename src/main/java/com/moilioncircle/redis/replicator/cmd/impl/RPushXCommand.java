package com.moilioncircle.redis.replicator.cmd.impl;

import com.moilioncircle.redis.replicator.cmd.Command;

/**
 * Created by leon on 2/2/17.
 */
public class RPushXCommand implements Command {
    private String key;
    private String value;

    public RPushXCommand() {
    }

    public RPushXCommand(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "RPushXCommand{" +
                "key='" + key + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
