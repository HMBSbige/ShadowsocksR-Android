package com.github.shadowsocks.database;


import com.j256.ormlite.field.DatabaseField;

public class SSRSub {
    @DatabaseField(generatedId = true)
    public int id = 0;

    @DatabaseField
    public String url = "";

    @DatabaseField
    public String url_group = "";
}
