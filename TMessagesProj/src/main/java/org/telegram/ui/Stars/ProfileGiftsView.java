package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.ui.Stars.StarsController.findAttribute;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.yumigram.YumiConfig;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ProfileButtonsGlass;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ProfileGiftsView extends View implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount;
    private final long dialogId;
    private final View avatarContainer;
    private final ProfileActivity.AvatarImageView avatarImage;
    private final Theme.ResourcesProvider resourcesProvider;

    private boolean active = true;
    private BlurredBackgroundDrawableViewFactory glassFactory;

    public void setGlassFactory(BlurredBackgroundDrawableViewFactory factory) {
        glassFactory = factory;
        for (Gift gift : gifts) {
            gift.glassDrawable = null;
        }
        invalidate();
    }
    public void setActive(boolean f) {
        this.active = f;
        if (!f && pressedGift != null) {
            pressedGift.bounce.setPressed(false);
            pressedGift.dragging = false;
            pressedGift.returning = false;
            pressedGift.dragScale = pressedGift.stretchX = pressedGift.stretchY = 1f;
            pressedGift = null;
            gestureOwned = gestureDragged = false;
            activePointerId = MotionEvent.INVALID_POINTER_ID;
            disallowParentIntercept(false);
        }
        invalidate();
    }

    public ProfileGiftsView(Context context, int currentAccount, long dialogId, @NonNull View avatarContainer, ProfileActivity.AvatarImageView avatarImage, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.currentAccount = currentAccount;
        this.dialogId = dialogId;

        this.avatarContainer = avatarContainer;
        this.avatarImage = avatarImage;

        this.resourcesProvider = resourcesProvider;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public float expandProgress, collapseProgress;
    public boolean isOpening;

    public void setExpandProgress(float progress) {
        if (this.expandProgress != progress) {
            this.expandProgress = progress;
            invalidate();
        }
    }

    public void setCollapseProgress(float progress, boolean isOpening) {
        this.isOpening = isOpening;
        float newProgress = Utilities.clamp01((progress - 0.3f) / 0.7f);
        if (this.collapseProgress != newProgress) {
            this.collapseProgress = newProgress;
            invalidate();
        }
    }

    private float actionBarProgress;

    public void setActionBarActionMode(float progress) {
//        if (Theme.isCurrentThemeDark()) {
//            return;
//        }
        actionBarProgress = progress;
        invalidate();
    }


    private float left, right, cy;
    private float expandY, maxExpandY;
    private final AnimatedFloat rightAnimated = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final int touchSlop;
    private float downX, downY, lastX, lastY;
    private long lastTouchTime;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;
    private boolean gestureOwned, gestureDragged;

    public void setBounds(float left, float right, float cy, boolean animated, int maxExpandY) {
        boolean changed = Math.abs(left - this.left) > 0.1f || Math.abs(right - this.right) > 0.1f || Math.abs(cy - this.cy) > 0.1f;
        this.left = left;
        this.right = right;
        if (!animated) {
            this.rightAnimated.set(this.right, true);
        }
        this.cy = cy;
        this.maxExpandY = maxExpandY + cy;
        if (changed) {
            invalidate();
        }
    }

    public void setExpandCoords(float y) {
        this.expandY = y;
        invalidate();
    }

    private float progressToInsets = 1f;

    public void setProgressToStoriesInsets(float progressToInsets) {
        if (this.progressToInsets == progressToInsets) {
            return;
        }
        this.progressToInsets = progressToInsets;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.starUserGiftsLoaded);

        for (Gift gift : gifts) {
            if (gift.starGiftDrawable != null && !gift.starGiftDrawableAttached) {
                gift.starGiftDrawable.onAttachedToWindow();
                gift.starGiftDrawableAttached = true;
            }
        }

        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starUserGiftsLoaded);

        for (Gift gift : gifts) {
            if (gift.starGiftDrawable != null && gift.starGiftDrawableAttached) {
                gift.starGiftDrawable.onDetachedToWindow();
                gift.starGiftDrawableAttached = false;
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.starUserGiftsLoaded) {
            if ((long) args[0] == dialogId) {
                update();
            }
        }
    }

    public final class Gift {

        public final long id;
        public final TL_stars.TL_starGiftUnique uniqueGift;
        public final TLRPC.Document document;
        public final long documentId;
        public final int color;
        public final String slug;
        private StarsReactionsSheet.Particles particles;

        public int position = -1;

        public Gift(TL_stars.TL_starGiftUnique gift) {
            id = gift.id;
            uniqueGift = gift;
            document = gift.getDocument();
            documentId = document == null ? 0 : document.id;
            final TL_stars.starGiftAttributeBackdrop backdrop = findAttribute(gift.attributes, TL_stars.starGiftAttributeBackdrop.class);
            color = backdrop.center_color | 0xFF000000;
            slug = gift.slug;
            initParticles();
        }

        public Gift(TLRPC.TL_emojiStatusCollectible status) {
            id = status.collectible_id;
            uniqueGift = null;
            document = null;
            documentId = status.document_id;
            color = status.center_color | 0xFF000000;
            slug = status.slug;
            initParticles();
        }

        private void initParticles() {
            particles = new StarsReactionsSheet.Particles(StarsReactionsSheet.Particles.TYPE_RADIAL, 6);
            final float gsz = dp(36);
            particles.bounds.set(-gsz / 2.0f, -gsz / 2.0f, gsz / 2.0f, gsz / 2.0f);
        }

        public boolean equals(Gift b) {
            return b != null && b.id == id && b.documentId == documentId && b.color == color && java.util.Objects.equals(b.slug, slug);
        }

        public RadialGradient gradient;
        public final Matrix gradientMatrix = new Matrix();
        public Paint gradientPaint;
        public StarGiftSheet.StarGiftDrawableIcon starGiftDrawable;
        private boolean starGiftDrawableAttached;
        public AnimatedFloat animatedFloat;

        public final RectF bounds = new RectF();
        public final ButtonBounce bounce = new ButtonBounce(ProfileGiftsView.this);
        private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Shader bubbleShader = new android.graphics.SweepGradient(0, 0,
                new int[]{0xFFFF8DE1, 0xFF8DEEFF, 0xFFFFF1A8, 0xFFB99CFF, 0xFFFF8DE1}, null);
        private BlurredBackgroundDrawable glassDrawable;
        private float dragX, dragY, velocityX, velocityY, dragOffsetX, dragOffsetY;
        private float tilt;
        private float dragScale = 1f, releaseScale = 1f, stretchX = 1f, stretchY = 1f;
        private boolean dragging, returning;
        private long physicsTime, releaseTime;

        public void copy(Gift b) {
            gradient = b.gradient;
            starGiftDrawable = b.starGiftDrawable;
            starGiftDrawableAttached = b.starGiftDrawableAttached;
            gradientPaint = b.gradientPaint;
            glassDrawable = b.glassDrawable;
            animatedFloat = b.animatedFloat;
            particles = b.particles;
            position = b.position;
        }

        public void draw(
                Canvas canvas,
                float cx, float cy,
                float ascale, float stretchX, float stretchY, float rotate,
                float alpha,
                float gradientAlpha
        ) {
            if (alpha <= 0.0f) {
                bounds.setEmpty();
                return;
            }
            final float gsz = dp(54);
            bounds.set(cx - gsz / 2, cy - gsz / 2, cx + gsz / 2, cy + gsz / 2);
            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(rotate);
            final float scale = ascale * bounce.getScale(0.1f);
            canvas.scale(scale * stretchX, scale * stretchY);
            particles.process();
            particles.draw(canvas, color, alpha);
            if (glassFactory != null) {
                if (glassDrawable == null) {
                    glassDrawable = glassFactory.create(ProfileGiftsView.this, ProfileButtonsGlass.NO_COLORS).setRadius(gsz / 2f);
                }
                glassDrawable.setBounds((int) -gsz / 2, (int) -gsz / 2, (int) gsz / 2, (int) gsz / 2);
                glassDrawable.setAlpha((int) (255 * alpha));
                glassDrawable.draw(canvas);
            }
            if (gradientPaint != null) {
                gradientPaint.setAlpha((int) (0xFF * alpha * gradientAlpha));
                canvas.drawCircle(0, 0, gsz / 2f, gradientPaint);
            }
            bubblePaint.setStyle(Paint.Style.FILL);
            bubblePaint.setColor(Theme.multAlpha(color, .13f * alpha));
            canvas.drawCircle(0, 0, gsz / 2f, bubblePaint);
            bubblePaint.setStyle(Paint.Style.STROKE);
            bubblePaint.setStrokeWidth(dp(2.1f));
            bubblePaint.setShader(bubbleShader);
            bubblePaint.setAlpha((int) (255 * alpha));
            canvas.drawCircle(0, 0, gsz / 2f - dp(1.2f), bubblePaint);
            bubblePaint.setShader(null);
            bubblePaint.setAlpha(255);
            bubblePaint.setStrokeWidth(dp(.8f));
            bubblePaint.setColor(Theme.multAlpha(Color.WHITE, .48f * alpha));
            canvas.drawArc(-gsz * .42f, -gsz * .42f, gsz * .42f, gsz * .42f, 205, 94, false, bubblePaint);
            bubblePaint.setStyle(Paint.Style.FILL);
            bubblePaint.setColor(Theme.multAlpha(Color.WHITE, .3f * alpha));
            canvas.drawOval(-gsz * .31f, -gsz * .37f, -gsz * .01f, -gsz * .26f, bubblePaint);
            bubblePaint.setColor(Theme.multAlpha(0xFFB4F6FF, .12f * alpha));
            canvas.drawOval(gsz * .13f, gsz * .12f, gsz * .4f, gsz * .35f, bubblePaint);
            if (starGiftDrawable != null) {
                int halfGiftSize = Math.round(gsz / 2f);
                starGiftDrawable.setBounds(-halfGiftSize, -halfGiftSize, halfGiftSize, halfGiftSize);
                canvas.saveLayerAlpha(-gsz / 2f, -gsz / 2f, gsz / 2f, gsz / 2f, (int) (255 * alpha));
                starGiftDrawable.draw(canvas);
                canvas.restore();
            }
            canvas.restore();
            final float hitRadiusX = gsz * .5f * Math.abs(scale * stretchX);
            final float hitRadiusY = gsz * .5f * Math.abs(scale * stretchY);
            bounds.set(cx - hitRadiusX, cy - hitRadiusY, cx + hitRadiusX, cy + hitRadiusY);
        }
    }

    private StarsController.GiftsList list;

    public final ArrayList<Gift> oldGifts = new ArrayList<>();
    public final ArrayList<Gift> gifts = new ArrayList<>();
    public final HashSet<Long> giftIds = new HashSet<>();
    public int maxCount;

    public void update() {
        if (dialogId <= 0 || !YumiConfig.isProfileV2Enabled()) {
            clearGifts();
            return;
        }
        if (!MessagesController.getInstance(currentAccount).enableGiftsInProfile) {
            clearGifts();
            return;
        }

        maxCount = MessagesController.getInstance(currentAccount).stargiftsPinnedToTopLimit;
        oldGifts.clear();
        oldGifts.addAll(gifts);
        gifts.clear();
        giftIds.clear();

        final TLRPC.EmojiStatus emojiStatus;
        if (dialogId >= 0) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            emojiStatus = user == null ? null : user.emoji_status;
        } else {
            final TLRPC.User chat = MessagesController.getInstance(currentAccount).getUser(-dialogId);
            emojiStatus = chat == null ? null : chat.emoji_status;
        }
        if (emojiStatus instanceof TLRPC.TL_emojiStatusCollectible) {
            giftIds.add(((TLRPC.TL_emojiStatusCollectible) emojiStatus).collectible_id);
        }
        list = StarsController.getInstance(currentAccount).getProfileGiftsList(dialogId);
        if (list != null) {
            for (int i = 0; i < list.gifts.size(); i++) {
                final TL_stars.SavedStarGift savedGift = list.gifts.get(i);
                if (!savedGift.unsaved && savedGift.pinned_to_top && savedGift.gift instanceof TL_stars.TL_starGiftUnique) {
                    final Gift gift = new Gift((TL_stars.TL_starGiftUnique) savedGift.gift);
                    if (!giftIds.contains(gift.id)) {
                        gifts.add(gift);
                        giftIds.add(gift.id);
                    }
                }
            }
        }

        boolean changed = false;
        if (gifts.size() != oldGifts.size()) {
            changed = true;
        } else for (int i = 0; i < gifts.size(); i++) {
            if (!gifts.get(i).equals(oldGifts.get(i))) {
                changed = true;
                break;
            }
        }

        for (int i = 0; i < gifts.size(); i++) {
            final Gift g = gifts.get(i);
            Gift oldGift = null;
            for (int j = 0; j < oldGifts.size(); ++j) {
                if (oldGifts.get(j).id == g.id) {
                    oldGift = oldGifts.get(j);
                    break;
                }
            }

            if (oldGift != null && oldGift.equals(g)) {
                g.copy(oldGift);
            } else {
                if (oldGift != null) {
                    g.position = oldGift.position;
                    if (oldGift.starGiftDrawable != null && oldGift.starGiftDrawableAttached) {
                        oldGift.starGiftDrawable.onDetachedToWindow();
                        oldGift.starGiftDrawableAttached = false;
                    }
                }
                g.gradient = new RadialGradient(0, 0, dp(22.5f), new int[]{g.color, Theme.multAlpha(g.color, 0.0f)}, new float[]{0, 1}, Shader.TileMode.CLAMP);
                g.gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                g.gradientPaint.setShader(g.gradient);
                g.starGiftDrawable = new StarGiftSheet.StarGiftDrawableIcon(ProfileGiftsView.this, g.uniqueGift, 54, .25f).setRounding(dp(27));
                g.starGiftDrawableAttached = isAttachedToWindow();
                g.animatedFloat = new AnimatedFloat(this, 0, 320, null);
                g.animatedFloat.force(0.0f);
            }
        }

        List<Integer> positions = new ArrayList<>();
        final int[] preferredGiftPositions = {1, 5, 0, 4, 2, 3};
        for (int position : preferredGiftPositions) {
            if (position < maxCount) positions.add(position);
        }
        for (int i = preferredGiftPositions.length; i < maxCount; i++) {
            positions.add(i);
        }

        for (int i = 0; i < oldGifts.size(); i++) {
            final Gift g = oldGifts.get(i);
            Gift newGift = null;
            for (int j = 0; j < gifts.size(); ++j) {
                if (gifts.get(j).id == g.id) {
                    newGift = gifts.get(j);
                    break;
                }
            }
            if (newGift == null) {
                if (g.starGiftDrawable != null && g.starGiftDrawableAttached) {
                    g.starGiftDrawable.onDetachedToWindow();
                    g.starGiftDrawableAttached = false;
                }
                g.starGiftDrawable = null;
                g.gradient = null;
            } else {
                positions.remove((Object) g.position);
            }
        }

        if (!positions.isEmpty()) {
            for (int i = 0; i < gifts.size(); ++i) {
                final Gift g = gifts.get(i);
                if (g.position == -1) {
                    g.position = positions.remove(0);
                }
            }
        }

        if (changed)
            invalidate();
    }

    private void clearGifts() {
        for (Gift gift : gifts) {
            if (gift.starGiftDrawable != null && gift.starGiftDrawableAttached) {
                gift.starGiftDrawable.onDetachedToWindow();
                gift.starGiftDrawableAttached = false;
            }
        }
        gifts.clear();
        oldGifts.clear();
        giftIds.clear();
        invalidate();
    }

    private final TimeInterpolator giftCollapseXInterpolator = new DecelerateInterpolator();
    private final TimeInterpolator giftCollapseYInterpolator = new LinearInterpolator();

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (!active || !YumiConfig.isProfileV2Enabled() || dialogId <= 0 || gifts.isEmpty() || expandProgress >= 1.0f || collapseProgress <= 0f) return;

        final float realX = avatarContainer.getX();
        final float realY = avatarContainer.getY();
        final float realW = (avatarContainer.getWidth()) * avatarContainer.getScaleX();
        final float realH = (avatarContainer.getHeight()) * avatarContainer.getScaleY();

        final float sz = dpf2(YumiConfig.isProfileV2Enabled() ? 60 : 96);
        final float ax = Math.min(realX, (getWidth() - sz) / 2f);
        final float ay = Math.max(realY, (maxExpandY - sz) / 2f);
        final float aw = Math.max(realW, sz);
        final float ah = Math.max(realH, sz);


        canvas.save();
        canvas.clipRect(0, 0, getWidth(), expandY);

        final float acx = ax + aw / 2.0f;
        final float acy = ay + ah / 2.0f;

        final float realCX = realX + realW / 2.0f;
        final float realCY = realY + realH / 2.0f;

        float expandScale = expandY / maxExpandY;
        float closedAlpha = Utilities.clamp01((expandY - (AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight())) / dp(50));

        boolean animateVisibleGifts = false;
        for (int i = 0; i < gifts.size(); ++i) {
            final Gift gift = gifts.get(i);
            final float enter = gift.animatedFloat.set(1.0f);
            float scale = lerp(0.5f, 1.0f, enter);
            final float alpha = enter * (1.0f - expandProgress) * (1.0f - actionBarProgress) * (closedAlpha);
            animateVisibleGifts |= alpha > 0.01f;

            float gx, gy;
            float delayValue;

            switch (gift.position) {
                case 0: // left
                    gx = acx / 2f - (dp(20) * expandScale);
                    gy = acy - dp(13);
                    delayValue = 1.6f;
                    break;
                case 1: // top left
                    gx = acx * 2f / 3f - (dp(6) * expandScale);
                    gy = ay - dp(4);
                    delayValue = 0;
                    break;
                case 2: // bottom left
                    gx = acx * 2f / 3f - (dp(12) * expandScale);
                    gy = ay + ah - dp(16);
                    delayValue = 0.9f;
                    break;
                case 3: // right
                    gx = acx * 1.5f + (dp(20) * expandScale);
                    gy = acy - dp(13);
                    delayValue = 1.6f;
                    break;
                case 4: // top right
                    gx = acx * 4f / 3f + (dp(12) * expandScale);
                    gy = ay - dp(4);
                    delayValue = 0.9f;
                    break;
                default: // bottom right
                    gx = acx * 4f / 3f + (dp(12) * expandScale);
                    gy = ay + ah - dp(16);
                    delayValue = 0;
                    break;
            }

            final float collapseProgressWithEnter;
            if (isOpening || enter >= 1f) {
                collapseProgressWithEnter = collapseProgress;
            } else {
                collapseProgressWithEnter = Math.min(enter, collapseProgress);
            }

            final float delayFraction = 0.2f;
            final float maxDelayFraction = 1.6f * delayFraction;
            final float intervalFraction = 1f - maxDelayFraction;

            float delay = delayValue * delayFraction;
            float collapse = collapseProgressWithEnter >= 1f - delay ? 1f
                    : Utilities.clamp01((collapseProgressWithEnter - maxDelayFraction + delay) / intervalFraction);
            if (collapse < 1f) {
                gx = AndroidUtilities.lerp(realCX, gx, giftCollapseXInterpolator.getInterpolation(collapse));
                gy = AndroidUtilities.lerp(realCY, gy, giftCollapseYInterpolator.getInterpolation(collapse));
                scale = AndroidUtilities.lerp(scale / 2f, scale, collapse);
            }

            if (gift.dragging) {
                gx = gift.dragX;
                gy = gift.dragY;
            } else if (gift.returning) {
                final long now = SystemClock.uptimeMillis();
                final float dt = Math.min(.04f, Math.max(.001f, (now - gift.physicsTime) / 1000f));
                gift.physicsTime = now;
                final float dx = gx - gift.dragX;
                final float dy = gy - gift.dragY;
                gift.velocityX += (dx * 32f - gift.velocityX * 8.8f) * dt;
                gift.velocityY += (dy * 32f - gift.velocityY * 8.8f) * dt;
                gift.dragX += gift.velocityX * dt;
                gift.dragY += gift.velocityY * dt;
                gx = gift.dragX;
                gy = gift.dragY;
                final float shrink = Utilities.clamp01((now - gift.releaseTime) / 520f);
                final float easedShrink = 1f - (1f - shrink) * (1f - shrink) * (1f - shrink);
                gift.dragScale = AndroidUtilities.lerp(gift.releaseScale, 1f, easedShrink);
                gift.stretchX = AndroidUtilities.lerp(gift.stretchX, 1f, Math.min(1f, dt * 8f));
                gift.stretchY = AndroidUtilities.lerp(gift.stretchY, 1f, Math.min(1f, dt * 8f));
                if (Math.abs(dx) < dp(.8f) && Math.abs(dy) < dp(.8f) && Math.abs(gift.velocityX) + Math.abs(gift.velocityY) < dp(8) && shrink >= 1f) {
                    gift.returning = false;
                    gift.dragScale = gift.stretchX = gift.stretchY = 1f;
                    gift.velocityX = gift.velocityY = 0;
                } else {
                    animateVisibleGifts = true;
                }
            } else if (YumiConfig.isProfileV2Enabled()) {
                final float phase = SystemClock.uptimeMillis() / 1050f + i * 1.47f;
                gx += (float) Math.sin(phase) * dp(3.5f);
                gy += (float) Math.cos(phase * .83f) * dp(5.5f);
            }

            float depthScale;
            switch (gift.position) {
                case 0: depthScale = .88f; break;
                case 1: depthScale = 1f; break;
                case 2: depthScale = .78f; break;
                case 3: depthScale = 1.06f; break;
                case 4: depthScale = .82f; break;
                default: depthScale = .94f; break;
            }
            if (gift.dragging) {
                gift.tilt = Math.max(-14f, Math.min(14f, gift.velocityX / dp(24f)));
            } else {
                gift.tilt *= .86f;
            }
            gift.draw(canvas, gx, gy, scale * depthScale * gift.dragScale, gift.stretchX, gift.stretchY, gift.tilt, alpha, 1f);
        }

        canvas.restore();
        if (animateVisibleGifts && active && YumiConfig.isProfileV2Enabled() && isAttachedToWindow()) {
            postInvalidateDelayed(34);
        }
    }

    public Gift getGiftUnder(float x, float y) {
        for (int i = gifts.size() - 1; i >= 0; --i) {
            Gift gift = gifts.get(i);
            final float dx = x - gift.bounds.centerX();
            final float dy = y - gift.bounds.centerY();
            final float radiusX = Math.max(1f, gift.bounds.width() * .5f);
            final float radiusY = Math.max(1f, gift.bounds.height() * .5f);
            if (gift.bounds.contains(x, y) && dx * dx / (radiusX * radiusX) + dy * dy / (radiusY * radiusY) <= 1f)
                return gift;
        }
        return null;
    }

    private Gift pressedGift;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!active || !YumiConfig.isProfileV2Enabled() || dialogId <= 0 || expandProgress >= 1f || collapseProgress <= 0f || actionBarProgress >= 1f) return false;
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pressedGift = getGiftUnder(event.getX(), event.getY());
            gestureOwned = pressedGift != null;
            gestureDragged = false;
            activePointerId = event.getPointerId(0);
            downX = lastX = event.getX();
            downY = lastY = event.getY();
            lastTouchTime = event.getEventTime();
            if (pressedGift != null) {
                pressedGift.bounce.setPressed(true);
                pressedGift.dragOffsetX = pressedGift.bounds.centerX() - downX;
                pressedGift.dragOffsetY = pressedGift.bounds.centerY() - downY;
                pressedGift.velocityX = pressedGift.velocityY = 0;
                pressedGift.returning = false;
                disallowParentIntercept(true);
                return true;
            }
            return false;
        } else if (action == MotionEvent.ACTION_MOVE && gestureOwned && pressedGift != null) {
            final int pointerIndex = event.findPointerIndex(activePointerId);
            if (pointerIndex < 0) return true;
            final float x = event.getX(pointerIndex);
            final float y = event.getY(pointerIndex);
            if (!gestureDragged && Math.hypot(x - downX, y - downY) > touchSlop) {
                gestureDragged = true;
                pressedGift.dragging = true;
                pressedGift.bounce.setPressed(false);
                disallowParentIntercept(true);
            }
            if (gestureDragged) {
                final float dt = Math.max(.001f, (event.getEventTime() - lastTouchTime) / 1000f);
                final float dx = x - downX;
                final float dy = y - downY;
                final float pull = Math.min(1f, (float) Math.hypot(dx, dy) / dp(190f));
                pressedGift.dragScale = 1f + 3.5f * pull;
                pressedGift.stretchX = Math.min(1.52f, 1f + Math.abs(dx) / dp(270f));
                pressedGift.stretchY = Math.min(1.52f, 1f + Math.abs(dy) / dp(270f));
                final float radiusX = dp(27f) * pressedGift.dragScale * pressedGift.stretchX;
                final float radiusY = dp(27f) * pressedGift.dragScale * pressedGift.stretchY;
                pressedGift.dragX = Math.max(Math.min(radiusX, getWidth() / 2f), Math.min(getWidth() - Math.min(radiusX, getWidth() / 2f), x + pressedGift.dragOffsetX));
                pressedGift.dragY = Math.max(Math.min(radiusY, getHeight() / 2f), Math.min(getHeight() - Math.min(radiusY, getHeight() / 2f), y + pressedGift.dragOffsetY));
                pressedGift.velocityX = (pressedGift.dragX - (lastX + pressedGift.dragOffsetX)) / dt;
                pressedGift.velocityY = (pressedGift.dragY - (lastY + pressedGift.dragOffsetY)) / dt;
                lastX = x;
                lastY = y;
                lastTouchTime = event.getEventTime();
                invalidate();
            }
            return true;
        } else if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && gestureOwned && pressedGift != null) {
            if (action == MotionEvent.ACTION_UP && !gestureDragged) {
                onGiftClick(pressedGift);
                pressedGift.bounce.setPressed(false);
            } else {
                pressedGift.dragging = false;
                pressedGift.returning = true;
                pressedGift.physicsTime = SystemClock.uptimeMillis();
                pressedGift.releaseTime = pressedGift.physicsTime;
                pressedGift.releaseScale = pressedGift.dragScale;
                pressedGift.velocityX *= .48f;
                pressedGift.velocityY *= .48f;
            }
            pressedGift = null;
            gestureOwned = gestureDragged = false;
            activePointerId = MotionEvent.INVALID_POINTER_ID;
            disallowParentIntercept(false);
            invalidate();
            return true;
        } else if (action == MotionEvent.ACTION_CANCEL) {
            if (pressedGift != null) {
                pressedGift.bounce.setPressed(false);
                if (pressedGift.dragging) {
                    pressedGift.dragging = false;
                    pressedGift.returning = true;
                    pressedGift.physicsTime = SystemClock.uptimeMillis();
                    pressedGift.releaseTime = pressedGift.physicsTime;
                    pressedGift.releaseScale = pressedGift.dragScale;
                }
                pressedGift = null;
            }
            gestureOwned = gestureDragged = false;
            activePointerId = MotionEvent.INVALID_POINTER_ID;
            disallowParentIntercept(false);
            invalidate();
            return true;
        }
        return gestureOwned;
    }

    private void disallowParentIntercept(boolean disallow) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(disallow);
        }
    }

    public void onGiftClick(Gift gift) {
        if (gift == null || gift.slug == null) {
            return;
        }
        new StarGiftSheet(getContext(), currentAccount, dialogId, resourcesProvider)
                .set(gift.slug, gift.uniqueGift, list)
                .show();
    }
}
