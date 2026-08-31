package com.polaris.timetable.ui.page;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.polaris.timetable.databinding.FragmentMyBinding;

/**
 * 「我的」页 Fragment（阶段 5-1/5-5）：ConstraintLayout 外壳 + ViewBinding + 委托 MyPageBuilder。
 * 视觉零变化，生命周期归属 Fragment view（onDestroyView 置空 binding），
 * 已切换至 androidx.fragment.app.Fragment + ConstraintLayout 自适应。
 */
public class MyPageFragment extends Fragment {

    private FragmentMyBinding binding;

    public static MyPageFragment newInstance() {
        return new MyPageFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMyBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!(getActivity() instanceof MyPageBuilder.Host)) {
            return;
        }
        MyPageBuilder.Host host = (MyPageBuilder.Host) getActivity();
        View builderView = new MyPageBuilder(host).build(getActivity());
        binding.myPageContainer.removeAllViews();
        binding.myPageContainer.addView(builderView,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        binding.myRoot.setBackgroundColor(host.pageSurfaceColor());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
