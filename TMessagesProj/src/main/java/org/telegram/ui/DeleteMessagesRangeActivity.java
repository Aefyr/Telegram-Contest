package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DeleteMessagesRangeActivity extends BaseFragment {

    private FrameLayout contentView;

    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private TextView bottomOverlayText;
    private SimpleTextView selectedDaysTextView;
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private TextPaint boldTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private TextPaint activeTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private TextPaint boldActiveTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private TextPaint textPaint2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private Paint blackoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Paint selectionIndicatorPaint;
    private Paint eraserPaint;

    private long dialogId;
    private boolean loading;
    private boolean checkEnterItems;


    Calendar startFromCalendar;
    int startFromYear;
    int startFromMonth;
    int monthCount;

    CalendarAdapter adapter;


    SparseArray<SparseArray<PeriodDay>> messagesByYearMounth = new SparseArray<>();
    boolean endReached;
    int startOffset = 0;
    int lastId;
    int minMontYear;
    private boolean isOpened;
    int selectedYear;
    int selectedMonth;

    private boolean isSelectionModeEnabled = false;
    private boolean isSelecting = false;
    private boolean hasSelection = false;

    private int selectionStartYear;
    private int selectionStartMonth;
    private int selectionStartDay;

    private int selectionFromYear;
    private int selectionFromMonth;
    private int selectionFromDay;

    private int selectionToYear;
    private int selectionToMonth;
    private int selectionToDay;

    private int selectionDayCount;

    private Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private Runnable recyclerAutoScrollRunnable = this::recyclerAutoScrollLoop;
    private boolean isAutoScrollingRecycler;
    private boolean isAutoScrollingRecyclerUp;
    private float autoScrollSpeedMultiplier = 1.0f;
    private float autoScrollSpeed = 100;

    private float lastSelectionMoveRawX;
    private float lastSelectionMoveRawY;

    public DeleteMessagesRangeActivity(Bundle args, int selectedDate) {
        super(args);

        if (selectedDate != 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(selectedDate * 1000L);
            selectedYear = calendar.get(Calendar.YEAR);
            selectedMonth = calendar.get(Calendar.MONTH);
        }

        selectionIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectionIndicatorPaint.setStyle(Paint.Style.STROKE);
        selectionIndicatorPaint.setStrokeWidth(AndroidUtilities.dp(2));

        autoScrollSpeed = AndroidUtilities.dp(16);

        eraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        eraserPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
    }

    @Override
    public boolean onFragmentCreate() {
        dialogId = getArguments().getLong("dialog_id");
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        textPaint.setTextSize(AndroidUtilities.dp(16));
        textPaint.setTextAlign(Paint.Align.CENTER);

        boldTextPaint.setTextSize(AndroidUtilities.dp(16));
        boldTextPaint.setTextAlign(Paint.Align.CENTER);
        boldTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        textPaint2.setTextSize(AndroidUtilities.dp(11));
        textPaint2.setTextAlign(Paint.Align.CENTER);
        textPaint2.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        activeTextPaint.setTextSize(AndroidUtilities.dp(16));
        activeTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        activeTextPaint.setTextAlign(Paint.Align.CENTER);

        boldActiveTextPaint.setTextSize(AndroidUtilities.dp(16));
        boldActiveTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        boldActiveTextPaint.setTextAlign(Paint.Align.CENTER);

        contentView = new FrameLayout(context);
        createActionBar(context);
        contentView.addView(actionBar);
        actionBar.setTitle(LocaleController.getString("Calendar", R.string.Calendar));
        actionBar.setCastShadows(false);
        actionBar.setBackButtonDrawable(new BackDrawable(false));

        //Action bar menu
        final ActionBarMenu actionMode = actionBar.createActionMode();
        selectedDaysTextView = new SimpleTextView(actionMode.getContext());
        selectedDaysTextView.setTextSize(20);
        selectedDaysTextView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        selectedDaysTextView.setTextColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon));
        selectedDaysTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        actionMode.addView(selectedDaysTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 65, 0, 0, 0));


        //Recycler
        listView = new RecyclerListView(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                checkEnterItems = false;
            }
        };
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
        layoutManager.setReverseLayout(true);
        listView.setAdapter(adapter = new CalendarAdapter());
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                checkLoadNext();

                if (isAutoScrollingRecycler && isSelecting) {
                    tryFindAndSetSelectionEndByRawXY(lastSelectionMoveRawX, lastSelectionMoveRawY);
                }
            }
        });

        FrameLayout recyclerContainer = new FrameLayout(context) {
            private float autoScrollRegionSize = 100f;

            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (isSelecting) {
                        tryFindAndSetSelectionEndByRawXY(event.getRawX(), event.getRawY());

                        if (event.getY() < autoScrollRegionSize) {
                            float clampedY = Math.max(event.getY(), 0f);
                            setAutoScrollSpeedMultiplier(1f - clampedY / autoScrollRegionSize);

                            startAutoScrollingRecycler(true);
                        } else if (getHeight() - event.getY() < autoScrollRegionSize) {
                            float clampedY = Math.min(event.getY(), getHeight());
                            setAutoScrollSpeedMultiplier(1f - ((getHeight() - clampedY) / autoScrollRegionSize));

                            startAutoScrollingRecycler(false);
                        } else {
                            stopAutoScrollingRecycler();
                        }

                        lastSelectionMoveRawX = event.getRawX();
                        lastSelectionMoveRawY = event.getRawY();

                        return true;
                    }


                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (isSelecting) {
                        tryFindAndSetSelectionEndByRawXY(event.getRawX(), event.getRawY());
                        stopSelection();

                        return true;
                    }
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (isSelecting) {
                        clearSelection();

                        return true;
                    }
                }

                return isSelecting;
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);

                autoScrollRegionSize = getMeasuredHeight() / 4f;
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {

                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (isSelecting) {
                        tryFindAndSetSelectionEndByRawXY(event.getRawX(), event.getRawY());

                        if (event.getY() < autoScrollRegionSize) {
                            float clampedY = Math.max(event.getY(), 0f);
                            setAutoScrollSpeedMultiplier(1f - clampedY / autoScrollRegionSize);

                            startAutoScrollingRecycler(true);
                        } else if (getHeight() - event.getY() < autoScrollRegionSize) {
                            float clampedY = Math.min(event.getY(), getHeight());
                            setAutoScrollSpeedMultiplier(1f - ((getHeight() - clampedY) / autoScrollRegionSize));

                            startAutoScrollingRecycler(false);
                        } else {
                            stopAutoScrollingRecycler();
                        }

                        lastSelectionMoveRawX = event.getRawX();
                        lastSelectionMoveRawY = event.getRawY();
                    }
                    return true;
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (isSelecting) {
                        tryFindAndSetSelectionEndByRawXY(event.getRawX(), event.getRawY());
                        stopSelection();
                    }
                    return true;
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (isSelecting) {
                        clearSelection();
                    }
                    return true;
                }


                return super.onTouchEvent(event);
            }
        };
        recyclerContainer.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 0, 0, 0));
        contentView.addView(recyclerContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 36, 0, 51));

        final String[] daysOfWeek = new String[]{
                LocaleController.getString("CalendarWeekNameShortMonday", R.string.CalendarWeekNameShortMonday),
                LocaleController.getString("CalendarWeekNameShortTuesday", R.string.CalendarWeekNameShortTuesday),
                LocaleController.getString("CalendarWeekNameShortWednesday", R.string.CalendarWeekNameShortWednesday),
                LocaleController.getString("CalendarWeekNameShortThursday", R.string.CalendarWeekNameShortThursday),
                LocaleController.getString("CalendarWeekNameShortFriday", R.string.CalendarWeekNameShortFriday),
                LocaleController.getString("CalendarWeekNameShortSaturday", R.string.CalendarWeekNameShortSaturday),
                LocaleController.getString("CalendarWeekNameShortSunday", R.string.CalendarWeekNameShortSunday),
        };

        Drawable headerShadowDrawable = ContextCompat.getDrawable(context, R.drawable.header_shadow).mutate();

        View calendarSignatureView = new View(context) {

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float xStep = getMeasuredWidth() / 7f;
                for (int i = 0; i < 7; i++) {
                    float cx = xStep * i + xStep / 2f;
                    float cy = (getMeasuredHeight() - AndroidUtilities.dp(2)) / 2f;
                    canvas.drawText(daysOfWeek[i], cx, cy + AndroidUtilities.dp(5), textPaint2);
                }
                headerShadowDrawable.setBounds(0, getMeasuredHeight() - AndroidUtilities.dp(3), getMeasuredWidth(), getMeasuredHeight());
                headerShadowDrawable.draw(canvas);
            }
        };
        contentView.addView(calendarSignatureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, 0, 0, 0, 0, 0));

        //Bottom button
        FrameLayout bottomOverlay = new FrameLayout(context) {
            @Override
            public void onDraw(Canvas canvas) {
                int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                Theme.chat_composeShadowDrawable.draw(canvas);
                canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.chat_composeBackgroundPaint);
            }
        };
        bottomOverlay.setWillNotDraw(false);
        bottomOverlay.setPadding(0, AndroidUtilities.dp(3), 0, 0);
        contentView.addView(bottomOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));
        bottomOverlay.setOnClickListener(view -> {
            if (!isSelectionModeEnabled) {
                setSelectionModeEnabled(true);
            } else {
                if (!hasSelection) {
                    return;
                }

                if (!DialogObject.isUserDialog(dialogId)) {
                    //This is illegal
                    finishFragment();
                    return;
                }

                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                if (user == null) {
                    return;
                }

                AlertsCreator.createDeleteMessagesRangeDialogAlert(this, selectionDayCount, user, getResourceProvider(), (deleteForOtherPerson) -> {
                    Toast.makeText(context, "Boom, your history is gone. As for your friend - " + deleteForOtherPerson, Toast.LENGTH_SHORT).show();
                    deleteMessagesForCurrentSelection(deleteForOtherPerson);
                    setSelectionModeEnabled(false);
                });
            }
        });

        bottomOverlayText = new TextView(context);
        bottomOverlayText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        bottomOverlayText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        bottomOverlayText.setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
        bottomOverlayText.setText(LocaleController.getString("Calendar", R.string.Calendar).toUpperCase());
        bottomOverlayText.setAllCaps(true);
        bottomOverlay.addView(bottomOverlayText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));


        //Misc
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (isSelectionModeEnabled) {
                        setSelectionModeEnabled(false);
                    } else {
                        finishFragment();
                    }
                }
            }
        });

        fragmentView = contentView;

        startFromCalendar = Calendar.getInstance();
        startFromYear = startFromCalendar.get(Calendar.YEAR);
        startFromMonth = startFromCalendar.get(Calendar.MONTH);

        if (selectedYear != 0) {
            monthCount = (startFromYear - selectedYear) * 12 + startFromMonth - selectedMonth + 1;
            layoutManager.scrollToPositionWithOffset(monthCount - 1, AndroidUtilities.dp(120));
        }
        if (monthCount < 3) {
            monthCount = 3;
        }


        loadNext();
        updateColors();
        updateBottomOverlay();
        updateActionBar();
        activeTextPaint.setColor(Color.WHITE);
        boldActiveTextPaint.setColor(Color.WHITE);
        return fragmentView;
    }

    private void updateColors() {
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        activeTextPaint.setColor(Color.WHITE);
        boldActiveTextPaint.setColor(Color.WHITE);
        textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        boldTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textPaint2.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        selectedDaysTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        BackDrawable backDrawable = new BackDrawable(false);
        backDrawable.setRotatedColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        backDrawable.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setBackButtonDrawable(backDrawable);
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_listSelector), false);
        selectionIndicatorPaint.setColor(Theme.getColor(Theme.key_checkboxSquareBackground));
        eraserPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        updateBottomOverlay();
    }

    private void beginSelection(int startYear, int startMonth, int startDay) {
        isSelecting = true;
        hasSelection = true;

        selectionStartYear = startYear;
        selectionStartMonth = startMonth;
        selectionStartDay = startDay;

        selectionFromYear = startYear;
        selectionFromMonth = startMonth;
        selectionFromDay = startDay;

        selectionToYear = startYear;
        selectionToMonth = startMonth;
        selectionToDay = startDay;

        recalculateDayCount();

        onSelectionChanged();
    }

    private void stopSelection() {
        if (isSelecting) {
            isSelecting = false;
        }

        if (isAutoScrollingRecycler) {
            stopAutoScrollingRecycler();
        }
    }

    private boolean tryFindAndSetSelectionEndByRawXY(float rawX, float rawY) {
        int[] location = new int[2];

        listView.getLocationOnScreen(location);
        float xMappedToRecycler = rawX - location[0];
        float yMappedToRecycler = rawY - location[1];

        MonthView monthView = (MonthView) listView.findChildViewUnder(xMappedToRecycler, yMappedToRecycler);
        if (monthView != null) {
            monthView.getLocationOnScreen(location);

            float xMappedToMonthView = rawX - location[0];
            float yMappedToMonthView = rawY - location[1];

            int hitDay = monthView.findDayUnder(xMappedToMonthView, yMappedToMonthView);
            if (hitDay != -1) {
                setSelectionEndTo(hitDay, monthView.currentMonthInYear, monthView.currentYear);
                return true;
            }
        }

        return false;
    }

    private void setSelectionEndTo(int endDay, int endMonth, int endYear) {
        boolean isEndDateBeforeStartDate = (endYear < selectionStartYear) || (endYear <= selectionStartYear && endMonth < selectionStartMonth) || (endYear <= selectionStartYear && endMonth <= selectionStartMonth && endDay <= selectionStartDay);
        boolean selectionChanged = false;
        if (isEndDateBeforeStartDate) {
            if (!(selectionFromDay == endDay && selectionFromMonth == endMonth && selectionFromYear == endYear && selectionToDay == selectionStartDay && selectionToMonth == selectionStartMonth && selectionToYear == selectionStartYear)) {
                selectionFromDay = endDay;
                selectionFromMonth = endMonth;
                selectionFromYear = endYear;

                selectionToDay = selectionStartDay;
                selectionToMonth = selectionStartMonth;
                selectionToYear = selectionStartYear;

                selectionChanged = true;
            }
        } else {
            if (!(selectionFromDay == selectionStartDay && selectionFromMonth == selectionStartMonth && selectionFromYear == selectionStartYear && selectionToDay == endDay && selectionToMonth == endMonth && selectionToYear == endYear)) {
                selectionFromDay = selectionStartDay;
                selectionFromMonth = selectionStartMonth;
                selectionFromYear = selectionStartYear;

                selectionToDay = endDay;
                selectionToMonth = endMonth;
                selectionToYear = endYear;

                selectionChanged = true;
            }
        }

        if (selectionChanged) {
            recalculateDayCount();

            onSelectionChanged();
        }
    }

    private void recalculateDayCount() {
        Date from = new Date(selectionFromYear, selectionFromMonth, selectionFromDay);
        Date to = new Date(selectionToYear, selectionToMonth, selectionToDay);
        long diffInMillies = Math.abs(from.getTime() - to.getTime());
        long diff = TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
        selectionDayCount = (int) diff + 1;
    }

    private void clearSelection() {
        if (isSelecting) {
            stopSelection();
        }

        hasSelection = false;
        onSelectionChanged();
    }

    private void onSelectionChanged() {
        listView.invalidateViews();

        updateBottomOverlay();
        updateActionBar();

        if (hasSelection) {
            contentView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private void setSelectionModeEnabled(boolean enabled) {
        if (enabled == isSelectionModeEnabled) {
            return;
        }

        isSelectionModeEnabled = enabled;
        onSelectionModeChanged();
    }

    private void onSelectionModeChanged() {
        if (!isSelectionModeEnabled) {
            clearSelection();
        }

        updateBottomOverlay();
        updateActionBar();
    }

    private void startAutoScrollingRecycler(boolean up) {
        if (isAutoScrollingRecycler && isAutoScrollingRecyclerUp == up) {
            return;
        } else if (isAutoScrollingRecycler && isAutoScrollingRecyclerUp != up) {
            stopAutoScrollingRecycler();

            isAutoScrollingRecycler = true;
            isAutoScrollingRecyclerUp = up;

            recyclerAutoScrollLoop();
        } else {
            isAutoScrollingRecycler = true;
            isAutoScrollingRecyclerUp = up;

            recyclerAutoScrollLoop();
        }
    }

    private void setAutoScrollSpeedMultiplier(float multiplier) {
        autoScrollSpeedMultiplier = multiplier;
    }

    private void recyclerAutoScrollLoop() {
        listView.scrollBy(0, (int) (autoScrollSpeedMultiplier * (isAutoScrollingRecyclerUp ? -autoScrollSpeed : autoScrollSpeed)));
        mainThreadHandler.postDelayed(recyclerAutoScrollRunnable, 16);
    }

    private void stopAutoScrollingRecycler() {
        if (!isAutoScrollingRecycler) {
            return;
        }

        listView.stopScroll();
        mainThreadHandler.removeCallbacks(recyclerAutoScrollRunnable);
        isAutoScrollingRecycler = false;
    }

    private void updateBottomOverlay() {
        if (bottomOverlayText != null) {
            if (!isSelectionModeEnabled) {
                bottomOverlayText.setAlpha(1f);
                bottomOverlayText.setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
                bottomOverlayText.setText(LocaleController.getString("DeleteRangeSelectDays", R.string.DeleteRangeSelectDays));
            } else {
                if (hasSelection) {
                    bottomOverlayText.setAlpha(1f);
                } else {
                    bottomOverlayText.setAlpha(0.5f);
                }
                bottomOverlayText.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                bottomOverlayText.setText(LocaleController.getString("DeleteRangeClearHistory", R.string.DeleteRangeClearHistory));
            }
        }
    }

    private void updateActionBar() {
        if (isSelectionModeEnabled) {
            actionBar.showActionMode(true);

            if (hasSelection) {
                selectedDaysTextView.setText(LocaleController.formatPluralString("DeleteRangeDays", selectionDayCount));
            } else {
                selectedDaysTextView.setText(LocaleController.getString("DeleteRangeSelectDays", R.string.DeleteRangeSelectDays));
            }

        } else {
            actionBar.hideActionMode();
        }
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return !isSelectionModeEnabled;
    }

    @Override
    public boolean onBackPressed() {
        if (isSelectionModeEnabled) {
            setSelectionModeEnabled(false);
            return false;
        }

        return true;
    }

    private void loadNext() {
        if (loading || endReached) {
            return;
        }
        loading = true;
        TLRPC.TL_messages_getSearchResultsCalendar req = new TLRPC.TL_messages_getSearchResultsCalendar();
        req.filter = new TLRPC.TL_inputMessagesFilterPhotoVideo();

        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        req.offset_id = lastId;

        Calendar calendar = Calendar.getInstance();
        listView.setItemAnimator(null);
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.TL_messages_searchResultsCalendar res = (TLRPC.TL_messages_searchResultsCalendar) response;

                for (int i = 0; i < res.periods.size(); i++) {
                    TLRPC.TL_searchResultsCalendarPeriod period = res.periods.get(i);
                    calendar.setTimeInMillis(period.date * 1000L);
                    int month = calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH);
                    SparseArray<PeriodDay> messagesByDays = messagesByYearMounth.get(month);
                    if (messagesByDays == null) {
                        messagesByDays = new SparseArray<>();
                        messagesByYearMounth.put(month, messagesByDays);
                    }
                    PeriodDay periodDay = new PeriodDay();
                    MessageObject messageObject = new MessageObject(currentAccount, res.messages.get(i), false, false);
                    periodDay.messageObject = messageObject;
                    startOffset += res.periods.get(i).count;
                    periodDay.startOffset = startOffset;
                    int index = calendar.get(Calendar.DAY_OF_MONTH) - 1;
                    if (messagesByDays.get(index, null) == null) {
                        messagesByDays.put(index, periodDay);
                    }
                    if (month < minMontYear || minMontYear == 0) {
                        minMontYear = month;
                    }

                }

                loading = false;
                if (!res.messages.isEmpty()) {
                    lastId = res.messages.get(res.messages.size() - 1).id;
                    endReached = false;
                    checkLoadNext();
                } else {
                    endReached = true;
                }
                if (isOpened) {
                    checkEnterItems = true;
                }
                listView.invalidate();
                int newMonthCount = (int) (((startFromCalendar.getTimeInMillis() / 1000) - res.min_date) / 2629800) + 1;
                adapter.notifyItemRangeChanged(0, monthCount);
                if (newMonthCount > monthCount) {
                    adapter.notifyItemRangeInserted(monthCount + 1, newMonthCount);
                    monthCount = newMonthCount;
                }
                if (endReached) {
                    resumeDelayedFragmentAnimation();
                }
            }
        }));
    }

    private void checkLoadNext() {
        if (loading || endReached) {
            return;
        }
        int listMinMonth = Integer.MAX_VALUE;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof MonthView) {
                int currentMonth = ((MonthView) child).currentYear * 100 + ((MonthView) child).currentMonthInYear;
                if (currentMonth < listMinMonth) {
                    listMinMonth = currentMonth;
                }
            }
        }
        ;
        int min1 = (minMontYear / 100 * 12) + minMontYear % 100;
        int min2 = (listMinMonth / 100 * 12) + listMinMonth % 100;
        if (min1 + 3 >= min2) {
            loadNext();
        }
    }

    private void deleteMessagesForCurrentSelection(boolean deleteForOthers) {
        Calendar fromCalendar = Calendar.getInstance();
        fromCalendar.set(selectionFromYear, selectionFromMonth, selectionFromDay, 0, 0, 1);

        Calendar toCalendar = Calendar.getInstance();
        toCalendar.set(selectionToYear, selectionToMonth, selectionToDay, 23, 59, 59);

        MessagesController.getInstance(currentAccount).deleteMessagesRange(dialogId, (int) (fromCalendar.getTimeInMillis() / 1000L), (int) (toCalendar.getTimeInMillis() / 1000L), deleteForOthers);
    }

    private class CalendarAdapter extends RecyclerView.Adapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new MonthView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            MonthView monthView = (MonthView) holder.itemView;

            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            if (month < 0) {
                month += 12;
                year--;
            }
            boolean animated = monthView.currentYear == year && monthView.currentMonthInYear == month;
            monthView.setDate(year, month, messagesByYearMounth.get(year * 100 + month), animated);
        }

        @Override
        public long getItemId(int position) {
            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            return year * 100L + month;
        }

        @Override
        public int getItemCount() {
            return monthCount;
        }
    }

    private class MonthView extends FrameLayout {

        SimpleTextView titleView;
        int currentYear;
        int currentMonthInYear;
        int daysInMonth;
        int startDayOfWeek;
        int cellCount;
        int startMonthTime;

        SparseArray<PeriodDay> messagesByDays = new SparseArray<>();
        SparseArray<ImageReceiver> imagesByDays = new SparseArray<>();

        SparseArray<PeriodDay> animatedFromMessagesByDays = new SparseArray<>();
        SparseArray<ImageReceiver> animatedFromImagesByDays = new SparseArray<>();

        boolean attached;
        float animationProgress = 1f;

        private Map<Integer, RectF> dayHitboxes = new HashMap<>(32);

        public MonthView(Context context) {
            super(context);
            setWillNotDraw(false);
            titleView = new SimpleTextView(context);
            titleView.setTextSize(15);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleView.setGravity(Gravity.CENTER);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 28, 0, 0, 12, 0, 4));
        }

        public void setDate(int year, int monthInYear, SparseArray<PeriodDay> messagesByDays, boolean animated) {
            boolean dateChanged = year != currentYear && monthInYear != currentMonthInYear;
            currentYear = year;
            currentMonthInYear = monthInYear;
            this.messagesByDays = messagesByDays;

            if (dateChanged) {
                if (imagesByDays != null) {
                    for (int i = 0; i < imagesByDays.size(); i++) {
                        imagesByDays.valueAt(i).onDetachedFromWindow();
                        imagesByDays.valueAt(i).setParentView(null);
                    }
                    imagesByDays = null;
                }
            }
            if (messagesByDays != null) {
                if (imagesByDays == null) {
                    imagesByDays = new SparseArray<>();
                }

                for (int i = 0; i < messagesByDays.size(); i++) {
                    int key = messagesByDays.keyAt(i);
                    if (imagesByDays.get(key, null) != null) {
                        continue;
                    }
                    ImageReceiver receiver = new ImageReceiver();
                    receiver.setParentView(this);
                    PeriodDay periodDay = messagesByDays.get(key);
                    MessageObject messageObject = periodDay.messageObject;
                    if (messageObject != null) {
                        if (messageObject.isVideo()) {
                            TLRPC.Document document = messageObject.getDocument();
                            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 50);
                            TLRPC.PhotoSize qualityThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320);
                            if (thumb == qualityThumb) {
                                qualityThumb = null;
                            }
                            if (thumb != null) {
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44", messageObject.strippedThumb, null, messageObject, 0);
                                } else {
                                    receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44", ImageLocation.getForDocument(thumb, document), "b", (String) null, messageObject, 0);
                                }
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && messageObject.messageOwner.media.photo != null && !messageObject.photoThumbs.isEmpty()) {
                            TLRPC.PhotoSize currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 50);
                            TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 320, false, currentPhotoObjectThumb, false);
                            if (messageObject.mediaExists || DownloadController.getInstance(currentAccount).canDownloadMedia(messageObject)) {
                                if (currentPhotoObject == currentPhotoObjectThumb) {
                                    currentPhotoObjectThumb = null;
                                }
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "44_44", null, null, messageObject.strippedThumb, currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                                } else {
                                    receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "44_44", ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                                }
                            } else {
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(null, null, messageObject.strippedThumb, null, messageObject, 0);
                                } else {
                                    receiver.setImage(null, null, ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", (String) null, messageObject, 0);
                                }
                            }
                        }
                        receiver.setRoundRadius(AndroidUtilities.dp(22));
                        imagesByDays.put(key, receiver);
                    }
                }
            }

            YearMonth yearMonthObject = YearMonth.of(year, monthInYear + 1);
            daysInMonth = yearMonthObject.lengthOfMonth();

            Calendar calendar = Calendar.getInstance();
            calendar.set(year, monthInYear, 0);
            startDayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 6) % 7;
            startMonthTime = (int) (calendar.getTimeInMillis() / 1000L);

            int totalColumns = daysInMonth + startDayOfWeek;
            cellCount = (int) (totalColumns / 7f) + (totalColumns % 7 == 0 ? 0 : 1);
            calendar.set(year, monthInYear + 1, 0);
            titleView.setText(LocaleController.formatYearMont(calendar.getTimeInMillis() / 1000, true));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(cellCount * (44 + 8) + 44), MeasureSpec.EXACTLY));
        }

        boolean pressed;
        float pressedX;
        float pressedY;

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (isSelectionModeEnabled) {
                    int hitDay = findDayUnder(event.getX(), event.getY());
                    if (hitDay == -1) {
                        return false;
                    }

                    beginSelection(currentYear, currentMonthInYear, hitDay);
                    return true;
                } else {
                    pressed = true;
                    pressedX = event.getX();
                    pressedY = event.getY();
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (pressed) {
                    for (int i = 0; i < imagesByDays.size(); i++) {
                        if (imagesByDays.valueAt(i).getDrawRegion().contains(pressedX, pressedY)) {
                            //Handle click
                        }
                    }
                }
                pressed = false;

                if (isSelecting) {
                    stopSelection();
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                pressed = false;
            }
            return pressed;
        }

        private int findDayUnder(float x, float y) {
            for (Map.Entry<Integer, RectF> entry : dayHitboxes.entrySet()) {
                if (entry.getValue().contains(x, y)) {
                    return entry.getKey();
                }
            }

            return -1;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int currentCell = 0;
            int currentColumn = startDayOfWeek;

            float xStep = getMeasuredWidth() / 7f;
            float yStep = AndroidUtilities.dp(44 + 8);
            dayHitboxes.clear();
            for (int i = 0; i < daysInMonth; i++) {
                float cx = xStep * currentColumn + xStep / 2f;
                float cy = yStep * currentCell + yStep / 2f + AndroidUtilities.dp(44);
                int currentDayInMonth = i + 1;
                RectF hitbox = new RectF(xStep * currentColumn, yStep * currentCell + AndroidUtilities.dp(44), xStep * currentColumn + xStep, yStep * currentCell + yStep + AndroidUtilities.dp(44));
                dayHitboxes.put(currentDayInMonth, hitbox);
                int nowTime = (int) (System.currentTimeMillis() / 1000L);
                boolean isFirstColumn = i == 0 || currentColumn == 0;
                boolean isLastColumn = currentColumn == 6 || i == daysInMonth - 1;
                boolean isSingleColumn = isFirstColumn && isLastColumn;

                boolean isSelected;
                if (hasSelection) {
                    boolean isAfterOrSameAsSelectFromDate = (currentYear > selectionFromYear) || (currentYear >= selectionFromYear && currentMonthInYear > selectionFromMonth) || (currentYear >= selectionFromYear && currentMonthInYear >= selectionFromMonth && currentDayInMonth >= selectionFromDay);
                    boolean isBeforeOrSameAsSelectToDate = (currentYear < selectionToYear) || (currentYear <= selectionToYear && currentMonthInYear < selectionToMonth) || (currentYear <= selectionToYear && currentMonthInYear <= selectionToMonth && currentDayInMonth <= selectionToDay);
                    isSelected = isAfterOrSameAsSelectFromDate && isBeforeOrSameAsSelectToDate;
                } else {
                    isSelected = false;
                }

                boolean isSelectionStart = isSelected && currentYear == selectionFromYear && currentMonthInYear == selectionFromMonth && currentDayInMonth == selectionFromDay;
                boolean isSelectionEnd = isSelected && currentYear == selectionToYear && currentMonthInYear == selectionToMonth && currentDayInMonth == selectionToDay;
                boolean isSingleSelection = isSelectionStart && isSelectionEnd;

                if (isSelected) {
                    //Draw selection highlight
                    if (!isSingleSelection) {
                        if (isSingleColumn) {
                            selectionIndicatorPaint.setColor(Theme.getColor(Theme.key_checkboxSquareBackground));
                            selectionIndicatorPaint.setAlpha(64);
                            selectionIndicatorPaint.setStyle(Paint.Style.FILL);
                            eraserPaint.setStyle(Paint.Style.FILL);
                            canvas.drawCircle(cx, cy, AndroidUtilities.dp(22), selectionIndicatorPaint);
                        } else if (isFirstColumn) {
                            selectionIndicatorPaint.setColor(Theme.getColor(Theme.key_checkboxSquareBackground));
                            selectionIndicatorPaint.setAlpha(64);
                            selectionIndicatorPaint.setStyle(Paint.Style.FILL);
                            eraserPaint.setStyle(Paint.Style.FILL);
                            canvas.drawCircle(cx, cy, AndroidUtilities.dp(22), selectionIndicatorPaint);
                            canvas.drawRect(cx, cy - AndroidUtilities.dp(22), xStep * currentColumn + xStep, cy + AndroidUtilities.dp(22), eraserPaint);
                            canvas.drawRect(cx, cy - AndroidUtilities.dp(22), xStep * currentColumn + xStep, cy + AndroidUtilities.dp(22), selectionIndicatorPaint);
                        } else if (isLastColumn) {
                            selectionIndicatorPaint.setColor(Theme.getColor(Theme.key_checkboxSquareBackground));
                            selectionIndicatorPaint.setAlpha(64);
                            selectionIndicatorPaint.setStyle(Paint.Style.FILL);
                            canvas.drawCircle(cx, cy, AndroidUtilities.dp(22), selectionIndicatorPaint);
                            canvas.drawRect(xStep * currentColumn - 1, cy - AndroidUtilities.dp(22), cx, cy + AndroidUtilities.dp(22), eraserPaint);
                            canvas.drawRect(xStep * currentColumn - 1, cy - AndroidUtilities.dp(22), cx, cy + AndroidUtilities.dp(22), selectionIndicatorPaint);
                        } else {
                            selectionIndicatorPaint.setColor(Theme.getColor(Theme.key_checkboxSquareBackground));
                            selectionIndicatorPaint.setAlpha(64);
                            selectionIndicatorPaint.setStyle(Paint.Style.FILL);
                            canvas.drawRect(xStep * currentColumn, cy - AndroidUtilities.dp(22), xStep * currentColumn + xStep, cy + AndroidUtilities.dp(22), selectionIndicatorPaint);
                        }
                    }

                    //Draw selection start/end
                    if (isSingleSelection) {
                        selectionIndicatorPaint.setColor(Theme.getColor(Theme.key_checkboxSquareBackground));
                        selectionIndicatorPaint.setAlpha(255);
                        selectionIndicatorPaint.setStyle(Paint.Style.STROKE);
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(20), selectionIndicatorPaint);
                    } else if (isSelectionStart) {
                        eraserPaint.setStyle(Paint.Style.FILL);
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(21), eraserPaint);
                        canvas.drawRect(xStep * currentColumn - 1f, cy - AndroidUtilities.dp(24) - 1f, cx + 1f, cy + AndroidUtilities.dp(24) + 1f, eraserPaint);

                        selectionIndicatorPaint.setColor(Theme.getColor(Theme.key_checkboxSquareBackground));
                        selectionIndicatorPaint.setAlpha(255);
                        selectionIndicatorPaint.setStyle(Paint.Style.STROKE);
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(20), selectionIndicatorPaint);
                    } else if (isSelectionEnd) {
                        eraserPaint.setStyle(Paint.Style.FILL);
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(21), eraserPaint);
                        canvas.drawRect(cx - 1f, cy - AndroidUtilities.dp(24) - 1f, xStep * currentColumn + xStep + 1f, cy + AndroidUtilities.dp(24) + 1f, eraserPaint);

                        selectionIndicatorPaint.setColor(Theme.getColor(Theme.key_checkboxSquareBackground));
                        selectionIndicatorPaint.setAlpha(255);
                        selectionIndicatorPaint.setStyle(Paint.Style.STROKE);
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(20), selectionIndicatorPaint);
                    }
                }

                if (nowTime < startMonthTime + (i + 1) * 86400) {
                    int oldAlpha = textPaint.getAlpha();
                    textPaint.setAlpha((int) (oldAlpha * 0.3f));
                    canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                    textPaint.setAlpha(oldAlpha);
                } else if (messagesByDays != null && messagesByDays.get(i, null) != null) {
                    float alpha = 1f;
                    if (imagesByDays.get(i) != null) {
                        if (checkEnterItems && !messagesByDays.get(i).wasDrawn) {
                            messagesByDays.get(i).enterAlpha = 0f;
                            messagesByDays.get(i).startEnterDelay = (cy + getY()) / listView.getMeasuredHeight() * 150;
                        }
                        if (messagesByDays.get(i).startEnterDelay > 0) {
                            messagesByDays.get(i).startEnterDelay -= 16;
                            if (messagesByDays.get(i).startEnterDelay < 0) {
                                messagesByDays.get(i).startEnterDelay = 0;
                            } else {
                                invalidate();
                            }
                        }
                        if (messagesByDays.get(i).startEnterDelay == 0 && messagesByDays.get(i).enterAlpha != 1f) {
                            messagesByDays.get(i).enterAlpha += 16 / 220f;
                            if (messagesByDays.get(i).enterAlpha > 1f) {
                                messagesByDays.get(i).enterAlpha = 1f;
                            } else {
                                invalidate();
                            }
                        }
                        alpha = messagesByDays.get(i).enterAlpha;
                        if (alpha != 1f) {
                            canvas.save();
                            float s = 0.8f + 0.2f * alpha;
                            canvas.scale(s, s, cx, cy);
                        } else if (isSelected) {
                            canvas.save();
                            canvas.scale(0.78f, 0.78f, cx, cy);
                        }

                        imagesByDays.get(i).setAlpha(messagesByDays.get(i).enterAlpha);
                        imagesByDays.get(i).setImageCoords(cx - AndroidUtilities.dp(42) / 2f, cy - AndroidUtilities.dp(42) / 2f, AndroidUtilities.dp(42), AndroidUtilities.dp(42));
                        imagesByDays.get(i).draw(canvas);
                        blackoutPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (messagesByDays.get(i).enterAlpha * 80)));
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(44) / 2f, blackoutPaint);
                        messagesByDays.get(i).wasDrawn = true;
                        if (alpha != 1f || isSelected) {
                            canvas.restore();
                        }
                    }
                    if (alpha != 1f) {
                        int oldAlpha = textPaint.getAlpha();
                        int oldBoldAlpha = boldTextPaint.getAlpha();
                        boldTextPaint.setAlpha((int) (oldAlpha * (1f - alpha)));
                        textPaint.setAlpha((int) (oldAlpha * (1f - alpha)));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), isSelected ? boldTextPaint : textPaint);
                        textPaint.setAlpha(oldAlpha);
                        boldTextPaint.setAlpha(oldBoldAlpha);

                        oldAlpha = textPaint.getAlpha();
                        oldBoldAlpha = boldActiveTextPaint.getAlpha();
                        activeTextPaint.setAlpha((int) (oldAlpha * alpha));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), isSelected ? boldActiveTextPaint : activeTextPaint);
                        activeTextPaint.setAlpha(oldAlpha);
                        boldActiveTextPaint.setAlpha(oldBoldAlpha);
                    } else {
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), isSelected ? boldActiveTextPaint : activeTextPaint);
                    }

                } else {
                    if (isSelectionStart || isSelectionEnd) {
                        selectionIndicatorPaint.setColor(Theme.getColor(Theme.key_checkboxSquareBackground));
                        selectionIndicatorPaint.setAlpha(255);
                        selectionIndicatorPaint.setStyle(Paint.Style.FILL);
                        canvas.save();
                        canvas.scale(0.74f, 0.74f, cx, cy);
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(22), selectionIndicatorPaint);
                        canvas.restore();

                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), boldActiveTextPaint);
                    } else {
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), isSelected ? boldTextPaint : textPaint);
                    }
                }

                currentColumn++;
                if (currentColumn >= 7) {
                    currentColumn = 0;
                    currentCell++;
                }
            }
        }

        private Path createRoundRectPath(float left, float top, float right, float bottom, float rx, float ry) {
            Path path = new Path();
            if (rx < 0) rx = 0;
            if (ry < 0) ry = 0;
            float width = right - left;
            float height = bottom - top;
            if (rx > width / 2) rx = width / 2;
            if (ry > height / 2) ry = height / 2;
            float widthMinusCorners = (width - (2 * rx));
            float heightMinusCorners = (height - (2 * ry));

            path.moveTo(right, top + ry);
            path.rQuadTo(0, -ry, -rx, -ry);
            path.rLineTo(-widthMinusCorners, 0);
            path.rQuadTo(-rx, 0, -rx, ry);
            path.rLineTo(0, heightMinusCorners);

            path.rQuadTo(0, ry, rx, ry);
            path.rLineTo(widthMinusCorners, 0);
            path.rQuadTo(rx, 0, rx, -ry);

            path.rLineTo(0, -heightMinusCorners);

            path.close();

            return path;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    imagesByDays.valueAt(i).onAttachedToWindow();
                }
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            attached = false;
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    imagesByDays.valueAt(i).onDetachedFromWindow();
                }
            }
        }
    }

    private class PeriodDay {
        MessageObject messageObject;
        int startOffset;
        float enterAlpha = 1f;
        float startEnterDelay = 1f;
        boolean wasDrawn;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {

        ThemeDescription.ThemeDescriptionDelegate descriptionDelegate = new ThemeDescription.ThemeDescriptionDelegate() {
            @Override
            public void didSetColor() {
                updateColors();
            }
        };
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhite);
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhiteBlackText);
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_listSelector);


        return super.getThemeDescriptions();
    }

    @Override
    public boolean needDelayOpenAnimation() {
        return true;
    }

    @Override
    protected void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        isOpened = true;
    }
}
