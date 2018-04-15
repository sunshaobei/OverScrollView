package com.liuzhenlin.overscrollview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.liuzhenlin.overscroll.SwipeMenuRecyclerView;

import me.slideback.activity.SlideBackActivity;

import static android.support.v7.widget.LinearLayoutManager.VERTICAL;
import static android.widget.Toast.LENGTH_SHORT;
import static com.liuzhenlin.overscrollview.util.ToastUtil.showToast;

public class SwipeMenuRecyclerActivity extends SlideBackActivity {
    private SwipeMenuRecyclerView mSwipeMenuRecyclerView;

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_swipe_menu_recycler);
        mSwipeMenuRecyclerView = findViewById(R.id.swipe_menu_recycler_view);
        mSwipeMenuRecyclerView.setAdapter(new SwipeMenuRecyclerAdapter());
        mSwipeMenuRecyclerView.setLayoutManager(new LinearLayoutManager(this, VERTICAL, false));
        mSwipeMenuRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mSwipeMenuRecyclerView.addItemDecoration(new DividerItemDecoration(this, VERTICAL));
    }

    public static class SwipeMenuRecyclerAdapter extends SwipeMenuRecyclerView
            .Adapter<SwipeMenuRecyclerAdapter.ViewHolder> implements View.OnClickListener {
        private static final String[] sTexts = {
                "SwipeMenuRecyclerView简介:",
                "1.item向左滑动显示\"置顶\"、\"删除\"...按钮",
                "2.item向右滑动后会自动回弹",
                "3.item左右滑动功能支持所有Vertical布局",
                "4.列表在最顶部下拉或最底部上拉有回弹效果",
                "5.快速滑动列表至最顶部或最底部会自动回弹",
                "6.过度滚动完美支持所有vertical布局",
                "7.Horizontal布局在最右端过度滚动时有bug,暂无解决办法！",
                "8.列表过度滚动回弹的效果可在xml或程序中禁用",
                "9.列表item左右滑动的功能也可在xml或程序中禁用",
                "10.item左右滑动和列表上下滑动操作不容易冲突",
                "11.支持addHeaderView(view)和addFooterView(view)",
                "12.默认不启用addHeader/FooterView功能,",
                "13.若要开启,可在xml布局文件中配置 use_header_and_footer_wrapper=\"true\"",
                "14.更详细的介绍，请前往：http://blog.csdn.net/freezeframe/article/details/79511479",
                "15.项目源码：https://github.com/freeze-frame/OverscrollView"
        };
        private Context mContext;

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            mContext = parent.getContext();
            return new ViewHolder(LayoutInflater.from(mContext).inflate(R.layout
                    .item_swipe_menu_recycler_view, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.mTextView.setText(sTexts[position]);
            holder.mTopButton.setTag(position);
            holder.mDeleteButton.setTag(position);
            holder.mTopButton.setOnClickListener(this);
            holder.mDeleteButton.setOnClickListener(this);
        }

        @Override
        public int getItemCount() {
            return sTexts.length;
        }

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.button_top:
                    showToast(mContext, "置顶 item" + v.getTag(), LENGTH_SHORT);
                    break;
                case R.id.button_delete:
                    showToast(mContext, "删除 item" + v.getTag(), LENGTH_SHORT);
                    break;
            }
        }

        public static class ViewHolder extends SwipeMenuRecyclerView.ViewHolder {
            private final TextView mTextView;
            private final Button mTopButton;
            private final Button mDeleteButton;

            public ViewHolder(View itemView) {
                super(itemView);
                mTextView = itemView.findViewById(R.id.text_ssll_rl);
                mTopButton = itemView.findViewById(R.id.button_top);
                mDeleteButton = itemView.findViewById(R.id.button_delete);
            }
        }
    }
}
