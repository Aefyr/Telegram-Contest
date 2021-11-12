package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextDetailCell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SendAsView extends FrameLayout {

    private static DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();

    private int currentAccount;
    private TLRPC.Chat currentChat;

    private SendAsPeerAdapter sendAsPeerAdapter;

    private TLRPC.Peer selectedSendAsPeer;
    TLRPC.TL_channels_sendAsPeers availableSendAsPeers;

    List<SendAsPeer> sendAsPeers = Collections.emptyList();

    private boolean nextShowWillForceRefresh = true;
    private boolean loadingPeers = false;
    private boolean reloadPeersAfterLoading = false;

    private OnDismissListener onDismissListener;

    private FlickerLoadingView flickerLoadingView;
    private FrameLayout wrappedFakePopupLayout;
    private RecyclerListView recycler;

    private AnimatorSet visibilityAnimation = new AnimatorSet();

    private boolean isShown = true;

    public SendAsView(@NonNull Context context, int currentAccount, TLRPC.Chat currentChat) {
        super(context);

        this.currentAccount = currentAccount;
        this.currentChat = currentChat;

        setBackgroundColor(0x54000000);
        setClickable(true);
        setOnTouchListener((view, motionEvent) -> {
            dismiss(true);
            return true;
        });

        //Setup fake popup
        Drawable shadowDrawable2 = ContextCompat.getDrawable(context, R.drawable.popup_fixed_alert).mutate();
        shadowDrawable2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground), PorterDuff.Mode.MULTIPLY));
        wrappedFakePopupLayout = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int heightMode = MeasureSpec.getMode(heightMeasureSpec);
                if (heightMode == MeasureSpec.AT_MOST) {
                    int height = Math.min(MeasureSpec.getSize(heightMeasureSpec), AndroidUtilities.dp(480));
                    super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(300), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
                } else {
                    super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(300), MeasureSpec.AT_MOST), heightMeasureSpec);
                }
                setPivotX(getMeasuredWidth() - AndroidUtilities.dp(8));
                setPivotY(AndroidUtilities.dp(8));
            }
        };
        wrappedFakePopupLayout.setBackground(shadowDrawable2);
        addView(wrappedFakePopupLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));

        //Setup linear container
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        wrappedFakePopupLayout.addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        //Setup header
        HeaderCell header = new HeaderCell(context, Theme.key_windowBackgroundWhiteBlueHeader, 17, 7, false);
        header.setText(LocaleController.getString("SendAsHeader", R.string.SendAsHeader));
        container.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        //Setup recycler container
        FrameLayout recyclerContainer = new FrameLayout(context);
        container.addView(recyclerContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f));

        //Setup recycler
        recycler = new RecyclerListView(getContext());
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int p = parent.getChildAdapterPosition(view);
                if (p == 0) {
                    outRect.top = AndroidUtilities.dp(4);
                }

                RecyclerView.Adapter adapter = parent.getAdapter();
                if (adapter != null && p == adapter.getItemCount() - 1) {
                    outRect.bottom = AndroidUtilities.dp(4);
                }
            }
        });
        recycler.setOnItemClickListener((view1, position) -> {
            SendAsPeer sendAsPeer = sendAsPeers.get(position);
            if (sendAsPeer != null) {
                MessagesController.getInstance(currentAccount).setDefaultSendAs(currentChat.id, sendAsPeer.getBackingPeer());
            }

            dismiss(true);
        });
        sendAsPeerAdapter = new SendAsPeerAdapter();
        recycler.setAdapter(sendAsPeerAdapter);
        recyclerContainer.addView(recycler, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        //Flicker
        flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setColors(Theme.key_actionBarDefaultSubmenuBackground, Theme.key_listSelector, null);
        flickerLoadingView.setViewType(FlickerLoadingView.USERS_TYPE);
        flickerLoadingView.setIsSingleCell(true);
        flickerLoadingView.showDate(false);
        flickerLoadingView.setItemsCount(3);
        recyclerContainer.addView(flickerLoadingView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 8, 0, 16, 0));

        invalidateState();
        dismiss(false);
    }

    public void setOnDismissListener(OnDismissListener onDismissListener) {
        this.onDismissListener = onDismissListener;
    }

    private void invalidateState() {
        if (loadingPeers) {
            flickerLoadingView.setVisibility(VISIBLE);
            recycler.setVisibility(GONE);
            return;
        }

        flickerLoadingView.setVisibility(GONE);
        recycler.setVisibility(VISIBLE);

        if (availableSendAsPeers == null) {
            sendAsPeerAdapter.setPeers(null);
            return;
        }

        Map<Long, TLRPC.Chat> chats = new HashMap<>();
        for (TLRPC.Chat chat : availableSendAsPeers.chats) {
            chats.put(chat.id, chat);
        }
        Map<Long, TLRPC.User> users = new HashMap<>();
        for (TLRPC.User user : availableSendAsPeers.users) {
            users.put(user.id, user);
        }

        sendAsPeers = new ArrayList<>();
        for (TLRPC.Peer peer : availableSendAsPeers.peers) {
            if (peer.channel_id != 0) {
                TLRPC.Chat chat = chats.get(peer.channel_id);
                if (chat != null) {
                    sendAsPeers.add(new ChatSendAsPeer(peer, selectedSendAsPeer != null && selectedSendAsPeer.channel_id == peer.channel_id, chat));
                }
            } else if (peer.chat_id != 0) {
                TLRPC.Chat chat = chats.get(peer.chat_id);
                if (chat != null) {
                    sendAsPeers.add(new ChatSendAsPeer(peer, selectedSendAsPeer != null && selectedSendAsPeer.chat_id == peer.chat_id, chat));
                }
            } else {
                TLRPC.User user = users.get(peer.user_id);
                if (user != null) {
                    sendAsPeers.add(new UserSendAsPeer(peer, selectedSendAsPeer != null && selectedSendAsPeer.user_id == peer.user_id, user));
                }
            }
        }

        sendAsPeerAdapter.setPeers(sendAsPeers);
    }

    public void setSelectedSendAsPeer(TLRPC.Peer peer) {
        selectedSendAsPeer = peer;
        invalidateState();
    }

    public void loadSendAsPeers() {
        if (loadingPeers) {
            reloadPeersAfterLoading = true;
            return;
        }

        loadingPeers = true;
        //Debounce loading
        AndroidUtilities.runOnUIThread(this::invalidateState, 200);

        TLRPC.TL_channels_getSendAs request = new TLRPC.TL_channels_getSendAs();
        request.peer = MessagesController.getInputPeer(currentChat);

        ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            {
                if (error != null) {
                    loadingPeers = false;
                    nextShowWillForceRefresh = true;
                    dismiss(true);
                    return;
                }

                TLRPC.TL_channels_sendAsPeers peers = (TLRPC.TL_channels_sendAsPeers) response;

                loadingPeers = false;
                availableSendAsPeers = peers;
                invalidateState();

                if (reloadPeersAfterLoading) {
                    reloadPeersAfterLoading = false;
                    loadSendAsPeers();
                }
            }
        }));
    }

    public void show(boolean refresh, boolean animate) {
        if (isShown) {
            return;
        }
        isShown = true;

        if (refresh || !nextShowWillForceRefresh) {
            nextShowWillForceRefresh = false;
            loadSendAsPeers();
        }

        if (visibilityAnimation != null) {
            visibilityAnimation.cancel();
            visibilityAnimation = null;
        }

        setVisibility(View.VISIBLE);

        if (animate) {
            visibilityAnimation = new AnimatorSet();
            visibilityAnimation.playTogether(
                    ObjectAnimator.ofFloat(wrappedFakePopupLayout, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(wrappedFakePopupLayout, View.TRANSLATION_Y, AndroidUtilities.dp(6), 0),
                    ObjectAnimator.ofFloat(this, View.ALPHA, 0f, 1f));
            visibilityAnimation.setDuration(180);
            visibilityAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    visibilityAnimation = null;
                }
            });
            visibilityAnimation.setInterpolator(decelerateInterpolator);
            visibilityAnimation.start();
        }
    }

    public void dismiss(boolean animate) {
        if (!isShown) {
            return;
        }

        isShown = false;

        if (visibilityAnimation != null) {
            visibilityAnimation.cancel();
            visibilityAnimation = null;
        }

        if (onDismissListener != null) {
            onDismissListener.onDismiss(this);
        }

        if (animate) {
            visibilityAnimation = new AnimatorSet();
            visibilityAnimation.playTogether(
                    ObjectAnimator.ofFloat(wrappedFakePopupLayout, View.ALPHA, 0f),
                    ObjectAnimator.ofFloat(wrappedFakePopupLayout, View.TRANSLATION_Y, AndroidUtilities.dp(5)),
                    ObjectAnimator.ofFloat(this, View.ALPHA, 0f));
            visibilityAnimation.setDuration(180);
            visibilityAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setVisibility(View.GONE);
                    visibilityAnimation = null;
                }
            });
            visibilityAnimation.setInterpolator(decelerateInterpolator);
            visibilityAnimation.start();
        } else {
            setVisibility(View.GONE);
        }
    }

    public boolean isShown() {
        return isShown;
    }

    private static class SendAsPeerAdapter extends RecyclerListView.SelectionAdapter {

        private List<SendAsPeer> peers;

        public void setPeers(List<SendAsPeer> sendAsPeers) {
            this.peers = sendAsPeers;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            SendAsPeerCell sendAsPeerCell = new SendAsPeerCell(parent.getContext());
            sendAsPeerCell.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            return new RecyclerListView.Holder(sendAsPeerCell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            SendAsPeerCell cell = (SendAsPeerCell) holder.itemView;
            cell.bind(peers.get(position));
        }

        @Override
        public int getItemCount() {
            return peers != null ? peers.size() : 0;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        private class SendAsPeerCell extends FrameLayout {

            private BackupImageView avatarImageView;
            private TextDetailCell detailCell;

            private AvatarDrawable avatarDrawable = new AvatarDrawable();

            private Paint selectionCirclePaint;

            private boolean isActive;

            public SendAsPeerCell(Context context) {
                super(context);

                selectionCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                selectionCirclePaint.setStyle(Paint.Style.STROKE);
                selectionCirclePaint.setStrokeWidth(AndroidUtilities.dp(2));

                avatarImageView = new BackupImageView(context);
                addView(avatarImageView, LayoutHelper.createFrame(44, 44, Gravity.CENTER_VERTICAL, 15, 0, 0, 0));
                avatarImageView.setRoundRadius(AndroidUtilities.dp(22));

                detailCell = new TextDetailCell(context);
                addView(detailCell, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 47, 0, 13, 0));


                setWillNotDraw(false);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(62), View.MeasureSpec.EXACTLY));
            }

            public void bind(SendAsPeer sendAsPeer) {
                isActive = sendAsPeer.isActive();

                if (sendAsPeer instanceof UserSendAsPeer) {
                    UserSendAsPeer userPeer = (UserSendAsPeer) sendAsPeer;
                    TLRPC.User user = userPeer.getUser();

                    avatarDrawable.setInfo(user);
                    ImageLocation imageLocation = ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL);
                    avatarImageView.setImage(imageLocation, "50_50", avatarDrawable, user);
                    detailCell.setTextAndValue(ContactsController.formatName(user.first_name, user.last_name), LocaleController.getString("SendAsPersonalAccount", R.string.SendAsPersonalAccount), false);
                } else if (sendAsPeer instanceof ChatSendAsPeer) {
                    ChatSendAsPeer chatPeer = (ChatSendAsPeer) sendAsPeer;
                    TLRPC.Chat chat = chatPeer.getChat();

                    avatarDrawable.setInfo(chat);
                    ImageLocation imageLocation = ImageLocation.getForChat(chat, ImageLocation.TYPE_SMALL);
                    avatarImageView.setImage(imageLocation, "50_50", avatarDrawable, chat);
                    detailCell.setTextAndValue(chat.title, LocaleController.formatPluralString("Subscribers", chat.participants_count), false);
                } else {
                    throw new IllegalArgumentException("Unsupported SendAsPeer subclass: " + sendAsPeer.getClass().getCanonicalName());
                }

                if (isActive) {
                    avatarImageView.setScaleX(0.82f);
                    avatarImageView.setScaleY(0.82f);
                } else {
                    avatarImageView.setScaleX(1f);
                    avatarImageView.setScaleY(1f);
                }

                invalidate();
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);

                if (isActive) {
                    selectionCirclePaint.setColor(Theme.getColor(Theme.key_checkboxSquareBackground));
                    float cx = avatarImageView.getLeft() + avatarImageView.getMeasuredWidth() / 2f;
                    float cy = avatarImageView.getTop() + avatarImageView.getMeasuredHeight() / 2f;
                    canvas.drawCircle(cx, cy, AndroidUtilities.dp(22), selectionCirclePaint);
                }
            }
        }
    }

    public interface OnDismissListener {
        void onDismiss(SendAsView sendAsView);
    }

    private abstract static class SendAsPeer {

        private TLRPC.Peer backingPeer;
        private boolean isActive;

        public SendAsPeer(TLRPC.Peer backingPeer, boolean isActive) {
            this.backingPeer = backingPeer;
            this.isActive = isActive;
        }

        public TLRPC.Peer getBackingPeer() {
            return backingPeer;
        }

        public boolean isActive() {
            return isActive;
        }
    }

    private static class UserSendAsPeer extends SendAsPeer {

        private TLRPC.User user;

        public UserSendAsPeer(TLRPC.Peer backingPeer, boolean isActive, TLRPC.User user) {
            super(backingPeer, isActive);
            this.user = user;
        }

        public TLRPC.User getUser() {
            return user;
        }
    }

    private static class ChatSendAsPeer extends SendAsPeer {

        private TLRPC.Chat chat;

        public ChatSendAsPeer(TLRPC.Peer backingPeer, boolean isActive, TLRPC.Chat chat) {
            super(backingPeer, isActive);
            this.chat = chat;
        }

        public TLRPC.Chat getChat() {
            return chat;
        }
    }


}
