package com.example.tvlanguagelearner;

import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class PlayerActivity extends AppCompatActivity implements TextOutput {

    private PlayerView playerView;
    private SimpleExoPlayer player;
    private TextView subtitleTextView;
    private TextView streamTitleTextView;

    private String streamUrl;
    private String streamName;
    private DefaultTrackSelector trackSelector;

    // Word highlighting variables
    private SpannableString currentSpannableSubtitle;
    private String currentSubtitleText = "";
    private Word[] currentWords;
    private int highlightedWordIndex = -1;
    private static final int HIGHLIGHT_COLOR = 0xFFFFFF00; // Yellow background

    // TextToSpeech for pronunciation
    private android.speech.tts.TextToSpeech tts;

    // Vocabulary list
    private java.util.Set<String> vocabularyWords = new java.util.HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.player_view);
        subtitleTextView = findViewById(R.id.subtitleTextView);
        streamTitleTextView = findViewById(R.id.streamTitleTextView);

        streamUrl = getIntent().getStringExtra("STREAM_URL");
        streamName = getIntent().getStringExtra("STREAM_NAME");

        streamTitleTextView.setText(streamName);
        streamTitleTextView.setSelected(true);

        initializePlayer();
        setupSubtitleClickListeners(); // Initialize click listeners
    }

    private void initializePlayer() {
        // Create track selector with subtitle support
        trackSelector = new DefaultTrackSelector(this);
        // Enable subtitle track selection
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setRendererDisabled(2, false) // Enable text renderer (index 2)
                .setSelectUndeterminedTextLanguage(true)
                .build());

        player = new SimpleExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .build();
        playerView.setPlayer(player);

        // Register this activity as text output listener
        player.addTextOutput(this);

        MediaItem mediaItem = MediaItem.fromUri(streamUrl);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();

        // Enable subtitle support in UI
        playerView.setShowSubtitleButton(true);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(com.google.android.exoplayer2.ExoPlaybackException error) {
                Toast.makeText(PlayerActivity.this, "播放错误: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    // Player is ready, start subtitle processing
                    startSubtitleProcessing();
                }
            }
        });
    }

    @Override
    public void onCues(List<Cue> cues) {
        if (!cues.isEmpty()) {
            Cue currentCue = cues.get(0);
            String subtitleText = currentCue.text != null ? currentCue.text.toString() : "";

            // Update subtitle text on UI thread
            subtitleTextView.post(() -> {
                // Process and highlight words
                processSubtitleForWordHighlight(subtitleText);
                subtitleTextView.setText(currentSpannableSubtitle);
                subtitleTextView.setMovementMethod(LinkMovementMethod.getInstance());
                // Store current subtitle for learning features
                subtitleTextView.setTag(R.id.subtitle_text_tag, subtitleText);
                currentSubtitleText = subtitleText;
            });
        } else {
            subtitleTextView.post(() -> {
                subtitleTextView.setText("无字幕");
                subtitleTextView.setTag(R.id.subtitle_text_tag, "");
                currentSubtitleText = "";
            });
        }
    }

    private void processSubtitleForWordHighlight(String subtitleText) {
        currentSpannableSubtitle = new SpannableString(subtitleText);
        currentWords = extractWords(subtitleText);

        // Make each word clickable
        for (int i = 0; i < currentWords.length; i++) {
            Word word = currentWords[i];
            if (word.text.length() > 0) {
                int start = word.startIndex;
                int end = word.endIndex;

                // Create clickable span for each word
                ClickableSpan clickableSpan = new WordClickableSpan(word.text, i);
                currentSpannableSubtitle.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private Word[] extractWords(String text) {
        List<Word> words = new java.util.ArrayList<>();
        Pattern pattern = Pattern.compile("\\b\\w+\\b", Pattern.UNICODE_CHARACTER_CLASS);
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            Word word = new Word(
                    matcher.group(),
                    matcher.start(),
                    matcher.end()
            );
            words.add(word);
        }

        return words.toArray(new Word[0]);
    }

    private class Word {
        String text;
        int startIndex;
        int endIndex;

        Word(String text, int startIndex, int endIndex) {
            this.text = text;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }

    private class WordClickableSpan extends ClickableSpan {
        private final String wordText;
        private final int wordIndex;

        WordClickableSpan(String wordText, int wordIndex) {
            this.wordText = wordText;
            this.wordIndex = wordIndex;
        }

        @Override
        public void onClick(View widget) {
            onWordClicked(wordText, wordIndex);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            // Customize the appearance of clickable words
            ds.setUnderlineText(false); // Remove underline
        }
    }

    private void startSubtitleProcessing() {
        // ExoPlayer automatically calls onCues when subtitle data is available
        // We'll also set up subtitle selection if available
        selectSubtitleTrack();
    }

    private void selectSubtitleTrack() {
        if (player == null || trackSelector == null) return;

        // Check if there are subtitle tracks available and select them
        // This is a simplified version - in production you'd want more robust track selection
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setRendererDisabled(2, false) // Ensure text renderer is enabled
                .setSelectUndeterminedTextLanguage(true)
                .build());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    // Language learning features
    public void onWordSelected(String word) {
        // Future: Implement word lookup, pronunciation, etc.
        Toast.makeText(this, "Selected: " + word, Toast.LENGTH_SHORT).show();
    }

    public void onSubtitleDoubleClick(String subtitleText) {
        // Future: Implement repeat, slow down, etc.
        Toast.makeText(this, "Repeating: " + subtitleText, Toast.LENGTH_SHORT).show();
    }

    // Initialize subtitle click listeners
    private void setupSubtitleClickListeners() {
        subtitleTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String currentSubtitle = (String) subtitleTextView.getTag(R.id.subtitle_text_tag);
                if (currentSubtitle != null && !currentSubtitle.isEmpty()) {
                    showSubtitleMenu(currentSubtitle);
                }
            }
        });

        // Long click for word selection
        subtitleTextView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                String currentSubtitle = (String) subtitleTextView.getTag(R.id.subtitle_text_tag);
                if (currentSubtitle != null && !currentSubtitle.isEmpty()) {
                    // Show word selection dialog or highlight feature
                    showWordSelectionDialog(currentSubtitle);
                }
                return true; // Consume the long click
            }
        });
    }

    private void showSubtitleMenu(String subtitleText) {
        // Simple menu for subtitle actions
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("字幕操作")
                .setItems(new String[]{"重复播放", "慢速播放", "翻译", "查看词典"}, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            repeatSubtitle(subtitleText);
                            break;
                        case 1:
                            slowDownPlayback();
                            break;
                        case 2:
                            translateSubtitle(subtitleText);
                            break;
                        case 3:
                            showDictionary(subtitleText);
                            break;
                    }
                })
                .show();
    }

    private void showWordSelectionDialog(String subtitleText) {
        // For now, show a simple toast with the full subtitle
        // In a full implementation, you'd parse individual words and let user select one
        Toast.makeText(this, "长按照片选择单词: " + subtitleText, Toast.LENGTH_LONG).show();
    }

    private void onWordClicked(String word, int wordIndex) {
        // Remove previous highlight
        if (highlightedWordIndex != -1 && currentSpannableSubtitle != null) {
            Word prevWord = currentWords[highlightedWordIndex];
            currentSpannableSubtitle.removeSpan(prevWord);
        }

        // Add highlight to clicked word
        if (currentSpannableSubtitle != null && currentWords != null && wordIndex < currentWords.length) {
            Word clickedWord = currentWords[wordIndex];
            BackgroundColorSpan highlightSpan = new BackgroundColorSpan(HIGHLIGHT_COLOR);
            currentSpannableSubtitle.setSpan(highlightSpan, clickedWord.startIndex, clickedWord.endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            subtitleTextView.setText(currentSpannableSubtitle);
            highlightedWordIndex = wordIndex;
        }

        // Show word learning options
        showWordLearningOptions(word);
    }

    private void showWordLearningOptions(String word) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("学习单词: " + word)
                .setItems(new String[]{"发音", "翻译", "添加到生词本", "查看详情"}, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            pronounceWord(word);
                            break;
                        case 1:
                            translateWord(word);
                            break;
                        case 2:
                            addToVocabulary(word);
                            break;
                        case 3:
                            showWordDetails(word);
                            break;
                    }
                })
                .show();
    }

    private void pronounceWord(String word) {
        // Use Android's TextToSpeech for pronunciation
        if (tts == null) {
            tts = new android.speech.tts.TextToSpeech(this, status -> {
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    tts.setLanguage(java.util.Locale.ENGLISH);
                    tts.speak(word, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null);
                }
            });
        } else {
            tts.speak(word, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null);
        }
        Toast.makeText(this, "发音: " + word, Toast.LENGTH_SHORT).show();
    }

    private void translateWord(String word) {
        // Show translation API selection
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("选择翻译API")
                .setItems(new String[]{"百度翻译", "Google翻译", "取消"}, (dialog, which) -> {
                    if (which == 0) {
                        translateWithBaidu(word);
                    } else if (which == 1) {
                        translateWithGoogle(word);
                    }
                })
                .show();
    }

    private void translateWithBaidu(String word) {
        showTranslationProgress();
        TranslationManager translationManager = new TranslationManager(new TranslationManager.TranslationCallback() {
            @Override
            public void onTranslationSuccess(String result) {
                dismissTranslationProgress();
                showTranslationResult(word, result, "百度翻译");
            }

            @Override
            public void onTranslationFailure(String error) {
                dismissTranslationProgress();
                Toast.makeText(PlayerActivity.this, "百度翻译失败: " + error, Toast.LENGTH_LONG).show();
            }
        });
        translationManager.translateEnToZh(word);
    }

    private void translateWithGoogle(String word) {
        showTranslationProgress();
        TranslationManager translationManager = new TranslationManager(new TranslationManager.TranslationCallback() {
            @Override
            public void onTranslationSuccess(String result) {
                dismissTranslationProgress();
                showTranslationResult(word, result, "Google翻译");
            }

            @Override
            public void onTranslationFailure(String error) {
                dismissTranslationProgress();
                Toast.makeText(PlayerActivity.this, "Google翻译失败: " + error, Toast.LENGTH_LONG).show();
            }
        });
        // Note: Google Translate API requires billing setup
        Toast.makeText(this, "Google翻译需要配置API密钥和账单", Toast.LENGTH_LONG).show();
        dismissTranslationProgress();
    }

    private void showTranslationResult(String originalWord, String translatedText, String apiName) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(apiName + " - " + originalWord)
                .setMessage("原文: " + originalWord + "\n\n翻译: " + translatedText)
                .setPositiveButton("确定", null)
                .setNeutralButton("播放发音", (dialog, which) -> {
                    pronounceWord(originalWord);
                })
                .show();
    }

    private android.app.ProgressDialog translationProgressDialog;

    private void showTranslationProgress() {
        translationProgressDialog = new android.app.ProgressDialog(this);
        translationProgressDialog.setMessage("正在翻译...");
        translationProgressDialog.setCancelable(false);
        translationProgressDialog.show();
    }

    private void dismissTranslationProgress() {
        if (translationProgressDialog != null && translationProgressDialog.isShowing()) {
            translationProgressDialog.dismiss();
        }
    }

    private void addToVocabulary(String word) {
        // Add word to vocabulary list
        vocabularyWords.add(word);
        Toast.makeText(this, "添加到生词本: " + word, Toast.LENGTH_SHORT).show();
    }

    private void showWordDetails(String word) {
        // Show detailed information about the word
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("单词详情: " + word)
                .setMessage("词性: 动词\n定义: 说, 讲\n例句: He speaks English fluently.\n同义词: talk, utter")
                .setPositiveButton("确定", null)
                .show();
    }

    private void repeatSubtitle(String subtitleText) {
        if (player != null) {
            long currentPosition = player.getCurrentPosition();
            // Rewind 5 seconds to repeat the current subtitle
            player.seekTo(Math.max(0, currentPosition - 5000));
            Toast.makeText(this, "重复播放", Toast.LENGTH_SHORT).show();
        }
    }

    private void slowDownPlayback() {
        if (player != null) {
            float currentSpeed = player.getPlaybackParameters().speed;
            float newSpeed = Math.max(0.5f, currentSpeed - 0.25f);
            player.setPlaybackParameters(new com.google.android.exoplayer2.PlaybackParameters(newSpeed));
            Toast.makeText(this, "慢速播放: " + newSpeed + "x", Toast.LENGTH_SHORT).show();
        }
    }

    private void translateSubtitle(String subtitleText) {
        // Placeholder for translation feature
        Toast.makeText(this, "翻译: " + subtitleText, Toast.LENGTH_LONG).show();
    }

    private void showDictionary(String subtitleText) {
        // Placeholder for dictionary feature
        Toast.makeText(this, "词典查询: " + subtitleText, Toast.LENGTH_LONG).show();
    }
}