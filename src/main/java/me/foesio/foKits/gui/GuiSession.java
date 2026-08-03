package me.foesio.foKits.gui;

import java.util.HashMap;
import java.util.Map;

public class GuiSession {
    private final GuiType type;
    private final String kitKey;
    private final Map<Integer, String> actions = new HashMap<>();
    private boolean cancelItemEditorSave;
    private int page;
    private String searchQuery = "";

    public GuiSession(GuiType type, String kitKey) {
        this.type = type;
        this.kitKey = kitKey;
    }

    public GuiType getType() {
        return type;
    }

    public String getKitKey() {
        return kitKey;
    }

    public Map<Integer, String> getActions() {
        return actions;
    }

    public boolean isCancelItemEditorSave() {
        return cancelItemEditorSave;
    }

    public void setCancelItemEditorSave(boolean cancelItemEditorSave) {
        this.cancelItemEditorSave = cancelItemEditorSave;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery == null ? "" : searchQuery.trim();
    }
}
