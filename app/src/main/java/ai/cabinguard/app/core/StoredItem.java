package ai.cabinguard.app.core;

/** 운전자가 차에 두겠다고 등록한 물품. */
public final class StoredItem {
    public final String name;
    public final HazardCategory category;
    /** 대시보드, 거치대처럼 직사광선을 받는 자리에 두었는지. */
    public final boolean directSun;

    public StoredItem(String name, HazardCategory category, boolean directSun) {
        this.name = name;
        this.category = category;
        this.directSun = directSun;
    }

    public StoredItem withDirectSun(boolean value) {
        return new StoredItem(name, category, value);
    }
}
