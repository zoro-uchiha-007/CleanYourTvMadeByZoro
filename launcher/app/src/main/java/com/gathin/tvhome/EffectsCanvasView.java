package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EffectsCanvasView extends View {

    public enum EffectType {
        NONE,
        SPIDERMAN,
        IRONMAN,
        ZORO,
        BYAKUYA,
        DOM,
        VIJAY
    }

    private EffectType currentEffect = EffectType.NONE;
    private float effectProgress = 0f;
    private ValueAnimator animator;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();

    // Anchor coordinates
    private View anchorViewRef;
    private float anchorX = 0f;
    private float anchorY = 0f;
    private float anchorWidth = 0f;
    private float anchorHeight = 0f;

    // Petals / shards for Byakuya
    private static class Petal {
        float x, y, size, speedY, speedX, rotation, rotSpeed;
        int color;
    }
    private final List<Petal> petals = new ArrayList<>();

    public EffectsCanvasView(Context context) {
        super(context);
        init();
    }

    public EffectsCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
    }

    public void triggerEffect(EffectType type, View anchorView) {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        currentEffect = type;
        effectProgress = 0f;
        this.anchorViewRef = anchorView;

        if (anchorView != null) {
            int[] anchorLoc = new int[2];
            anchorView.getLocationOnScreen(anchorLoc);
            int[] canvasLoc = new int[2];
            getLocationOnScreen(canvasLoc);

            anchorX = anchorLoc[0] - canvasLoc[0] + (anchorView.getWidth() / 2f);
            anchorY = anchorLoc[1] - canvasLoc[1];
            anchorWidth = anchorView.getWidth();
            anchorHeight = anchorView.getHeight();
        }

        if (type == EffectType.BYAKUYA) {
            initByakuyaPetals();
        }

        long duration = 750L;
        if (type == EffectType.ZORO) duration = 650L;
        else if (type == EffectType.IRONMAN) duration = 700L;
        else if (type == EffectType.VIJAY) duration = 800L;

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setRepeatCount(0); // Play ONLY ONCE when focused!
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            effectProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void clearEffect() {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        currentEffect = EffectType.NONE;
        effectProgress = 0f;
        invalidate();
    }

    // Whole-page petals for Byakuya
    private void initByakuyaPetals() {
        petals.clear();
        int width = Math.max(getWidth(), 1280);
        int height = Math.max(getHeight(), 720);
        for (int i = 0; i < 48; i++) {
            Petal p = new Petal();
            p.x = random.nextFloat() * width;
            p.y = random.nextFloat() * (height * 0.75f);
            p.size = 12f + random.nextFloat() * 24f;
            p.speedY = 1.5f + random.nextFloat() * 3.5f;
            p.speedX = -1.8f + random.nextFloat() * 3.6f;
            p.rotation = random.nextFloat() * 360f;
            p.rotSpeed = -4f + random.nextFloat() * 8f;
            int[] colors = new int[]{0xCCFF80AB, 0xCCEA80FC, 0xCCFFFFFF, 0xCCF48FB1};
            p.color = colors[random.nextInt(colors.length)];
            petals.add(p);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (currentEffect == EffectType.NONE || effectProgress >= 1f) {
            return;
        }

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // Dynamically track anchor position on every frame as parent scrolls
        if (anchorViewRef != null && anchorViewRef.isShown()) {
            int[] anchorLoc = new int[2];
            anchorViewRef.getLocationOnScreen(anchorLoc);
            int[] canvasLoc = new int[2];
            getLocationOnScreen(canvasLoc);

            anchorX = anchorLoc[0] - canvasLoc[0] + (anchorViewRef.getWidth() / 2f);
            anchorY = anchorLoc[1] - canvasLoc[1];
            anchorWidth = anchorViewRef.getWidth();
            anchorHeight = anchorViewRef.getHeight();
        }

        float ax = (anchorX > 0) ? anchorX : w / 2f;
        float ay = (anchorY > 0) ? anchorY : h * 0.45f;

        switch (currentEffect) {
            case ZORO:
                // Whole page triple emerald sword slashes across the entire frame
                drawWholePageZoroSlashes(canvas, w, h);
                break;
            case BYAKUYA:
                // Whole page Sakura petals & crystal shatter across the entire frame
                drawWholePageByakuya(canvas);
                break;
            case DOM:
                // Nitro / speed exhaust burst appearing directly behind his back (not whole page)
                drawDomNitroBehindBack(canvas, ax, ay);
                break;
            case SPIDERMAN:
                // Spidey sense curved warning arcs directly ABOVE his head
                drawSpiderSenseAboveHead(canvas, ax, ay);
                break;
            case IRONMAN:
                // Whole screen movie blast like Tony Stark blasted in movie
                drawIronManWholeScreenBlast(canvas, w, h, ax, ay);
                break;
            case VIJAY:
                // BLAST EFFECT BEHIND HIM (Shockwave blast explosion)
                drawVijayBlastBehindBody(canvas, ax, ay);
                break;
        }
    }

    // 1. Zoro: WHOLE PAGE triple sword slashes cutting diagonally across frame
    private void drawWholePageZoroSlashes(Canvas canvas, int w, int h) {
        float alpha = (1f - effectProgress);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.argb((int) (alpha * 245), 0, 255, 170));

        float offset = effectProgress * w * 0.65f;
        float[][] slashes = new float[][]{
                {w * 0.12f, h * 0.22f, w * 0.58f, h * 0.68f},
                {w * 0.08f, h * 0.35f, w * 0.65f, h * 0.78f},
                {w * 0.20f, h * 0.16f, w * 0.52f, h * 0.54f}
        };

        for (int i = 0; i < 3; i++) {
            paint.setStrokeWidth(8f - i * 1.5f);
            float startX = slashes[i][0] + (i * 35f);
            float startY = slashes[i][1];
            float endX = startX + offset;
            float endY = startY + offset * 0.75f;
            canvas.drawLine(startX, startY, endX, endY, paint);

            // Bright core line
            paint.setColor(Color.argb((int) (alpha * 255), 255, 255, 255));
            paint.setStrokeWidth(3.5f);
            canvas.drawLine(startX, startY, endX, endY, paint);
            paint.setColor(Color.argb((int) (alpha * 245), 0, 255, 170));
        }
    }

    // 2. Byakuya: WHOLE PAGE Sakura petals / glass crystals drifting
    private void drawWholePageByakuya(Canvas canvas) {
        float alpha = (1f - effectProgress);
        paint.setStyle(Paint.Style.FILL);

        for (Petal p : petals) {
            float curX = p.x + p.speedX * effectProgress * 65f;
            float curY = p.y + p.speedY * effectProgress * 95f;
            int a = (int) (alpha * Color.alpha(p.color));
            paint.setColor(Color.argb(a, Color.red(p.color), Color.green(p.color), Color.blue(p.color)));

            canvas.save();
            canvas.translate(curX, curY);
            canvas.rotate(p.rotation + p.rotSpeed * effectProgress * 30f);

            Path path = new Path();
            float half = p.size / 2f;
            path.moveTo(0, -half * 1.4f);
            path.lineTo(half, 0);
            path.lineTo(0, half * 1.4f);
            path.lineTo(-half, 0);
            path.close();
            canvas.drawPath(path, paint);

            canvas.restore();
        }
    }

    // 3. Dom: Nitro flame & high-speed streaks appearing directly BEHIND HIS BACK
    private void drawDomNitroBehindBack(Canvas canvas, float ax, float ay) {
        float backX = ax - 25f;
        float backY = ay + (anchorHeight * 0.38f);
        float alpha = (1f - effectProgress);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);

        // Multiple high-speed exhaust streaks blasting backward behind his back
        float[] yOffsets = new float[]{-35f, -18f, 0f, 18f, 35f};
        for (int i = 0; i < yOffsets.length; i++) {
            float y = backY + yOffsets[i];
            float burstLen = 80f + (effectProgress * 140f) + (i % 2 == 0 ? 30f : 0f);
            float startX = backX - (effectProgress * 25f);
            float endX = startX - burstLen;

            int color = (i == 2) ? 0xFFFFFFFF : (i % 2 == 0) ? 0xFFFF9100 : 0xFFFFD54F;
            paint.setColor(Color.argb((int) (alpha * 240), Color.red(color), Color.green(color), Color.blue(color)));
            paint.setStrokeWidth(5.5f - (effectProgress * 2.5f));
            canvas.drawLine(startX, y, endX, y, paint);
        }

        // Localized fiery amber aura directly behind his shoulders/back
        RadialGradient nitroCore = new RadialGradient(
                backX - 20f, backY, 50f,
                new int[]{Color.argb((int) (alpha * 200), 255, 255, 255), Color.argb((int) (alpha * 180), 255, 145, 0), Color.TRANSPARENT},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP
        );
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(nitroCore);
        canvas.drawCircle(backX - 20f, backY, 50f, paint);
        paint.setShader(null);
    }

    // 4. Spider-Man: Spidey-sense curved arcs radiating directly ABOVE his head
    private void drawSpiderSenseAboveHead(Canvas canvas, float ax, float ay) {
        float alpha = (1f - effectProgress);
        int red = Color.argb((int) (alpha * 245), 255, 30, 40);
        int blue = Color.argb((int) (alpha * 225), 30, 144, 255);

        // Position headX right above Spidey's head, lowered closer to head
        float headX = ax + (anchorWidth * 0.035f);
        float headY = ay + 26f; // Lowered to sit snugly right above top of mask

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);

        float[] angles = new float[]{-70, -45, -20, 0, 20, 45, 70};
        for (int i = 0; i < angles.length; i++) {
            float angle = angles[i];
            float rad = (float) Math.toRadians(angle - 90);
            float baseDist = 14f + (effectProgress * 48f);
            float len = 32f - (effectProgress * 12f);

            paint.setColor(i % 2 == 0 ? red : blue);
            paint.setStrokeWidth(5.5f - (effectProgress * 2.5f));

            float x1 = headX + (float) Math.cos(rad) * baseDist;
            float y1 = headY + (float) Math.sin(rad) * baseDist;
            float x2 = headX + (float) Math.cos(rad) * (baseDist + len);
            float y2 = headY + (float) Math.sin(rad) * (baseDist + len);

            canvas.drawLine(x1, y1, x2, y2, paint);
        }
    }

    // 5. Iron Man: WHOLE SCREEN MOVIE BLAST (Tony Stark Jericho / repulsor explosion)
    private void drawIronManWholeScreenBlast(Canvas canvas, int w, int h, float ax, float ay) {
        float alpha = (1f - effectProgress);
        float chestX = ax - 4f;
        float chestY = ay + (anchorHeight * 0.32f);

        // 1. Initial screen-wide blinding white/gold flash (first 28%)
        if (effectProgress < 0.28f) {
            float flashAlpha = (1f - (effectProgress / 0.28f)) * 0.75f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb((int) (flashAlpha * 255), 255, 255, 255));
            canvas.drawRect(0, 0, w, h, paint);
        }

        // 2. Full-screen expanding shockwave blast rings
        float maxDist = (float) Math.hypot(w, h);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);

        for (int i = 0; i < 4; i++) {
            float ringProgress = Math.max(0f, effectProgress - (i * 0.08f));
            float ringRadius = ringProgress * maxDist * 0.85f;
            float ringAlpha = Math.max(0f, (1f - ringProgress) * alpha);

            // Outer cyan repulsor shockwave
            paint.setColor(Color.argb((int) (ringAlpha * 230), 0, 229, 255));
            paint.setStrokeWidth(9f - i * 1.8f);
            canvas.drawCircle(chestX, chestY, ringRadius, paint);

            // Core fiery white shockwave
            paint.setColor(Color.argb((int) (ringAlpha * 255), 255, 250, 220));
            paint.setStrokeWidth(4.5f);
            canvas.drawCircle(chestX, chestY, ringRadius, paint);
        }

        // 3. High-velocity repulsor beams radiating across the full screen
        paint.setColor(Color.argb((int) (alpha * 240), 0, 229, 255));
        for (int r = 0; r < 14; r++) {
            double angle = (r * (360.0 / 14.0)) * Math.PI / 180.0;
            float startDist = 35f + effectProgress * 45f;
            float endDist = startDist + (effectProgress * maxDist * 0.60f);
            float x1 = chestX + (float) Math.cos(angle) * startDist;
            float y1 = chestY + (float) Math.sin(angle) * startDist;
            float x2 = chestX + (float) Math.cos(angle) * endDist;
            float y2 = chestY + (float) Math.sin(angle) * endDist;

            paint.setStrokeWidth(6f - (effectProgress * 3.5f));
            canvas.drawLine(x1, y1, x2, y2, paint);
        }

        // 4. Arc Reactor chest fireball
        float coreRadius = (1f - effectProgress) * 75f;
        RadialGradient blastGrad = new RadialGradient(
                chestX, chestY, Math.max(1f, coreRadius * 2f),
                new int[]{
                        Color.argb((int) (alpha * 255), 255, 255, 255),
                        Color.argb((int) (alpha * 245), 0, 229, 255),
                        Color.argb((int) (alpha * 160), 255, 180, 0),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.25f, 0.65f, 1f},
                Shader.TileMode.CLAMP
        );
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(blastGrad);
        canvas.drawCircle(chestX, chestY, Math.max(1f, coreRadius * 2f), paint);
        paint.setShader(null);
    }

    // 6. Vijay (JD): BLAST EFFECT BEHIND HIM (Shockwave blast explosion)
    private void drawVijayBlastBehindBody(Canvas canvas, float ax, float ay) {
        float bodyX = ax;
        float bodyY = ay + (anchorHeight * 0.50f);
        float alpha = (1f - effectProgress);

        // 1. Expanding shockwave ring 1
        float ring1Radius = 25f + (effectProgress * 160f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.argb((int) (alpha * 255), 255, 140, 0));
        paint.setStrokeWidth(9f * (1f - effectProgress * 0.7f));
        canvas.drawCircle(bodyX, bodyY, ring1Radius, paint);

        // 2. Expanding shockwave ring 2 (inner fire ring)
        float ring2Radius = 15f + (effectProgress * 110f);
        paint.setColor(Color.argb((int) (alpha * 255), 255, 210, 50));
        paint.setStrokeWidth(5f * (1f - effectProgress * 0.5f));
        canvas.drawCircle(bodyX, bodyY, ring2Radius, paint);

        // 3. Central fireball burst
        RadialGradient blastCore = new RadialGradient(
                bodyX, bodyY, ring2Radius,
                new int[]{
                        Color.argb((int) (alpha * 240), 255, 255, 220),
                        Color.argb((int) (alpha * 200), 255, 100, 0),
                        Color.TRANSPARENT
                },
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP
        );
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(blastCore);
        canvas.drawCircle(bodyX, bodyY, ring2Radius, paint);
        paint.setShader(null);

        // 4. Jagged blast sparks shooting outwards
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.argb((int) (alpha * 230), 255, 180, 20));
        paint.setStrokeWidth(3.5f);
        float[] sparkAngles = new float[]{-140, -110, -70, -30, 0, 30, 70, 110, 140, 180};
        for (float angle : sparkAngles) {
            float rad = (float) Math.toRadians(angle);
            float dist1 = ring1Radius * 0.6f;
            float dist2 = ring1Radius * 1.15f;
            float x1 = bodyX + (float) Math.cos(rad) * dist1;
            float y1 = bodyY + (float) Math.sin(rad) * dist1;
            float x2 = bodyX + (float) Math.cos(rad) * dist2;
            float y2 = bodyY + (float) Math.sin(rad) * dist2;
            canvas.drawLine(x1, y1, x2, y2, paint);
        }
    }
}
