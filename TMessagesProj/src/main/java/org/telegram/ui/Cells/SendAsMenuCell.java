package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;

public class SendAsMenuCell extends View {

    private final int currentAccount;

    private Drawable deleteDrawable;
    private ImageReceiver imageReceiver;
    private AvatarDrawable avatarDrawable;

    private int backColor;
    private static Paint backPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float closeProgress;
    private float hiddenProgress;
    private long lastUpdateTime;

    private TLRPC.Peer currentPeer;
    private boolean isOpen = true;
    private boolean isHidden = false;

    public SendAsMenuCell(@NonNull Context context, int currentAccount) {
        super(context);

        this.currentAccount = currentAccount;

        setClickable(true);

        deleteDrawable = getResources().getDrawable(R.drawable.delete);

        avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(AndroidUtilities.dp(14));

        imageReceiver = new ImageReceiver();
        imageReceiver.setRoundRadius(AndroidUtilities.dp(16));
        imageReceiver.setParentView(this);
        imageReceiver.setImageCoords(AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(32), AndroidUtilities.dp(32));

        updateColors();

        setOpen(false);
    }

    public void updateColors() {
        backColor = (Theme.getColor(Theme.key_chat_messagePanelVoiceBackground));
        int delete = Theme.getColor(Theme.key_chat_messagePanelVoicePressed);

        deleteDrawable.setColorFilter(new PorterDuffColorFilter(delete, PorterDuff.Mode.MULTIPLY));
        backPaint.setColor(backColor);
    }

    public void setCurrentPeer(TLRPC.Peer peer, boolean animated) {
        //TODO check if avatar has changed to avoid unnecessary work
        if (currentPeer != null && peer != null) {
            if (peer.channel_id != 0 && currentPeer.channel_id == peer.channel_id) {
                animated = false;
            } else if (peer.chat_id != 0 && currentPeer.chat_id == peer.chat_id) {
                animated = false;
            } else if (peer.user_id != 0 && currentPeer.user_id == peer.user_id) {
                animated = false;
            }
        }

        if (currentPeer == null || peer == null) {
            animated = false;
        }

        currentPeer = peer;

        if (animated) {
            hide();
        } else {
            isHidden = false;
            hiddenProgress = 0f;
            applyCurrentPeer();
            invalidate();
        }
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void setOpen(boolean open) {
        if (open == isOpen) {
            return;
        }

        isOpen = open;

        lastUpdateTime = System.currentTimeMillis();
        invalidate();
    }

    private void hide() {
        if (isHidden) {
            return;
        }

        isHidden = true;

        if (hiddenProgress >= 1.0f) {
            hiddenProgress = 1.0f;
            onFullyHidden();
        }

        invalidate();
    }

    private void show() {
        if (!isHidden) {
            return;
        }

        isHidden = false;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(40), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
    }

    private void onFullyHidden() {
        applyCurrentPeer();
        show();
    }

    private void applyCurrentPeer() {
        if (currentPeer != null) {
            if (currentPeer.user_id != 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(currentPeer.user_id);
                if (user != null) {
                    avatarDrawable.setInfo(user);
                    ImageLocation imageLocation = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL);
                    imageReceiver.setImage(imageLocation, "50_50", avatarDrawable, 0, null, user, 1);
                }
            } else if (currentPeer.channel_id != 0) {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(currentPeer.channel_id);
                if (chat != null) {
                    avatarDrawable.setInfo(chat);
                    ImageLocation imageLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL);
                    imageReceiver.setImage(imageLocation, "50_50", avatarDrawable, 0, null, chat, 1);
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(currentPeer.chat_id);
                if (chat != null) {
                    avatarDrawable.setInfo(chat);
                    ImageLocation imageLocation = ImageLocation.getForUserOrChat(chat, ImageLocation.TYPE_SMALL);
                    imageReceiver.setImage(imageLocation, "50_50", avatarDrawable, 0, null, chat, 1);
                }
            }
        } else {
            imageReceiver.clearImage();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isOpen && closeProgress != 1.0f || !isOpen && closeProgress != 0.0f || isHidden && hiddenProgress != 1.0f || !isHidden && hiddenProgress != 0.0f) {
            long newTime = System.currentTimeMillis();
            long dt = newTime - lastUpdateTime;
            if (dt < 0 || dt > 17) {
                dt = 17;
            }
            if (isOpen) {
                closeProgress += dt / 120.0f;
                if (closeProgress >= 1.0f) {
                    closeProgress = 1.0f;
                }
            } else {
                closeProgress -= dt / 120.0f;
                if (closeProgress < 0.0f) {
                    closeProgress = 0.0f;
                }
            }

            if (isHidden) {
                float oldHiddenProgress = hiddenProgress;

                hiddenProgress += dt / 120.0f;
                if (hiddenProgress >= 1.0f) {
                    hiddenProgress = 1.0f;

                    if (oldHiddenProgress < 1.0f) {
                        onFullyHidden();
                    }
                }
            } else {
                hiddenProgress -= dt / 120.0f;
                if (hiddenProgress < 0.0f) {
                    hiddenProgress = 0.0f;
                }
            }

            invalidate();
        }
        canvas.save();
        canvas.scale((1.0f - hiddenProgress), (1.0f - hiddenProgress), AndroidUtilities.dp(20), AndroidUtilities.dp(24));
        imageReceiver.draw(canvas);
        if (closeProgress != 0) {
            float alpha = Color.alpha(backColor) / 255.0f;
            backPaint.setAlpha((int) (255 * closeProgress * alpha));
            canvas.drawCircle(AndroidUtilities.dp(20), AndroidUtilities.dp(24), AndroidUtilities.dp(16), backPaint);
            canvas.save();
            canvas.rotate(45 * (1.0f - closeProgress), AndroidUtilities.dp(20), AndroidUtilities.dp(24));
            deleteDrawable.setBounds(AndroidUtilities.dp(13), AndroidUtilities.dp(17), AndroidUtilities.dp(27), AndroidUtilities.dp(31));
            deleteDrawable.setAlpha((int) (255 * closeProgress));
            deleteDrawable.draw(canvas);
            canvas.restore();
        }
        canvas.restore();
    }
}
