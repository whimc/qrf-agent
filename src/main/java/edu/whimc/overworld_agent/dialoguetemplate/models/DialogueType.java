package edu.whimc.overworld_agent.dialoguetemplate.models;

import org.apache.commons.lang3.StringUtils;

public enum DialogueType {

        GUIDE,

        BUILDER
        ;

        @Override
        public String toString() {
            return StringUtils.capitalize(super.toString().toLowerCase());
        }
}
