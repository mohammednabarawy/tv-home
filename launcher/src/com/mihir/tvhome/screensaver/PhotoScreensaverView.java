package com.mihir.tvhome.screensaver;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline HD slideshow. Holds two decoded photos, and releases them when dismissed. */
public final class PhotoScreensaverView extends FrameLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> photos = new ArrayList<>();
    private final ImageView[] layers = new ImageView[2];
    private final Bitmap[] bitmaps = new Bitmap[2];
    private ExecutorService decoder;
    private int index, front;
    private boolean active;
    private final Runnable rotate = this::loadNext;

    public PhotoScreensaverView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        setContentDescription(context.getString(com.mihir.tvhome.R.string.screensaver_name));
        for (int i = 0; i < layers.length; i++) {
            layers[i] = new ImageView(context);
            layers[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
            layers[i].setAlpha(0f);
            addView(layers[i], new LayoutParams(-1, -1));
        }
        File folder = context.getExternalFilesDir("screensaver");
        File[] custom = folder != null ? folder.listFiles() : null;
        if (custom != null) for (File photo : custom) {
            String name = photo.getName().toLowerCase(java.util.Locale.ROOT);
            if (photo.isFile() && (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp"))) {
                photos.add(photo.getAbsolutePath());
            }
        }
        if (photos.isEmpty()) try {
            String[] files = context.getAssets().list("wallpapers");
            if (files != null) for (String name : files) photos.add("wallpapers/" + name);
        } catch (IOException ignored) { }
        Collections.shuffle(photos);
        android.util.Log.i("PhotoScreensaver", "Available photos: " + photos.size());
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        active = true;
        decoder = Executors.newSingleThreadExecutor();
        loadNext();
    }

    private void loadNext() {
        if (!active || photos.isEmpty()) return;
        String path = photos.get(index++ % photos.size());
        decoder.execute(() -> {
            Bitmap bitmap = null;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream in = openPhoto(path)) { BitmapFactory.decodeStream(in, null, bounds); }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                options.inSampleSize = 1;
                while (bounds.outWidth / (options.inSampleSize * 2) >= 1920
                        && bounds.outHeight / (options.inSampleSize * 2) >= 1080) options.inSampleSize *= 2;
                try (InputStream in = openPhoto(path)) { bitmap = BitmapFactory.decodeStream(in, null, options); }
                if (bitmap != null) {
                    float scale = Math.max(1920f / bitmap.getWidth(), 1080f / bitmap.getHeight());
                    if (scale < 1f) {
                        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * scale), Math.round(bitmap.getHeight() * scale), true);
                        if (scaled != bitmap) bitmap.recycle();
                        bitmap = scaled;
                    }
                }
            } catch (IOException ignored) { }
            Bitmap result = bitmap;
            handler.post(() -> {
                if (!active) {
                    if (result != null) result.recycle();
                    return;
                }
                if (result != null) {
                    android.util.Log.i("PhotoScreensaver", "Displaying " + result.getWidth() + "x" + result.getHeight());
                    int next = 1 - front;
                    layers[next].animate().cancel();
                    layers[next].setImageDrawable(null);
                    if (bitmaps[next] != null) bitmaps[next].recycle();
                    bitmaps[next] = result;
                    layers[next].setImageBitmap(result);
                    layers[next].setAlpha(0f);
                    layers[next].bringToFront();
                    layers[next].animate().alpha(1f).setDuration(1200).start();
                    front = next;
                }
                handler.postDelayed(rotate, 30000);
            });
        });
    }

    private InputStream openPhoto(String path) throws IOException {
        return path.startsWith("/") ? new FileInputStream(path) : getContext().getAssets().open(path);
    }

    @Override protected void onDetachedFromWindow() {
        active = false;
        handler.removeCallbacksAndMessages(null);
        if (decoder != null) decoder.shutdownNow();
        for (int i = 0; i < layers.length; i++) {
            layers[i].animate().cancel();
            layers[i].setImageDrawable(null);
            if (bitmaps[i] != null) bitmaps[i].recycle();
            bitmaps[i] = null;
        }
        super.onDetachedFromWindow();
    }
}
