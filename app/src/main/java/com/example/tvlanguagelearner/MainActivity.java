package com.example.tvlanguagelearner;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private ListView streamListView;
    private String[] streamNames = {
            "BBC News",
            "CNN International",
            "BBC World News",
            "Fox News",
            "Al Jazeera English",
            "NHK WORLD",          // 新增 - 日本国际广播，100%英文字幕支持
            "France24",           // 新增 - 法国国际电视台，英文字幕完善
            "Bloomberg"           // 新增 - 财经新闻，专业字幕系统
    };

    private String[] streamUrls = {
            "http://bbcnews.co.uk/live/meta/live/en/live/rss/tv",
            "http://rss.cnn.com/rss/edition.rss",
            "http://bbc.co.uk/programmes/p02nrsct",
            "http://feeds.foxnews.com/foxnews/latest",
            "http://www.aljazeera.com/xml/rss/allcontent.xml",
            "https://www3.nhk.or.jp/rss/news/cat0.xml",          // NHK WORLD
            "https://www.france24.com/en/rss-feeds",            // France24
            "https://www.bloomberg.com/rss/"                    // Bloomberg
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        streamListView = findViewById(R.id.streamListView);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                streamNames
        );

        streamListView.setAdapter(adapter);

        streamListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String selectedStreamUrl = streamUrls[position];
                String selectedStreamName = streamNames[position];

                Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                intent.putExtra("STREAM_URL", selectedStreamUrl);
                intent.putExtra("STREAM_NAME", selectedStreamName);
                startActivity(intent);
            }
        });
    }
}