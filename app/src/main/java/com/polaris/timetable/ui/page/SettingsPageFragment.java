package com.polaris.timetable.ui.page;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.polaris.timetable.databinding.FragmentSettingsBinding;

/**
 * 「设置」页 Fragment（阶段 5-2/5-5）：ConstraintLayout 外壳 + ViewBinding + 委托 SettingsPageBuilder。
 * 按参数展示 4 类面板，视觉一致，生命周期归属 Fragment view。
 * 已切换至 androidx.fragment.app.Fragment + ConstraintLayout 自适应。
 */
public class SettingsPageFragment extends Fragment {

    private static final String ARG_PANEL = "panel"; // schedule | global | security | more | shellAdvanced | frameAdvanced
    private static final String ARG_TITLE = "title";

    private FragmentSettingsBinding binding;
    private String panel = "schedule";
    private String title = "";

    public static SettingsPageFragment newInstance(String panel, String title) {
        SettingsPageFragment f = new SettingsPageFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PANEL, panel);
        args.putString(ARG_TITLE, title);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            panel = getArguments().getString(ARG_PANEL, "schedule");
            title = getArguments().getString(ARG_TITLE, "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (!(getActivity() instanceof SettingsPageBuilder.Host)) {
            return;
        }
        SettingsPageBuilder.Host host = (SettingsPageBuilder.Host) getActivity();
        // 仅保留容器背景，内容由 Builder 按 panel 构建后注入
        int bg = 0xFFEAF2FF;
        if (getActivity() instanceof com.polaris.timetable.ui.page.MyPageBuilder.Host) {
            bg = ((com.polaris.timetable.ui.page.MyPageBuilder.Host) getActivity()).pageSurfaceColor();
        }
        binding.settingsRoot.setBackgroundColor(bg);
        LinearLayout panelView = null;
        SettingsPageBuilder builder = new SettingsPageBuilder(host);
        switch (panel) {
            case "schedule":
                panelView = builder.createScheduleSettingsPanel(getActivity());
                break;
            case "global":
                panelView = builder.createGlobalSettingsPanel(getActivity());
                break;
            case "shellAdvanced":
                panelView = builder.buildAdvancedShellSettings(getActivity());
                break;
            case "frameAdvanced":
                panelView = builder.buildAdvancedScheduleFrameSettings(getActivity());
                break;
            case "security":
                panelView = builder.createSecuritySettingsPanel(getActivity());
                break;
            case "more":
                panelView = builder.createMoreSettingsPanel(getActivity());
                break;
            default:
                panelView = new LinearLayout(getActivity());
                break;
        }
        binding.settingsPageContainer.removeAllViews();
        if (panelView != null) {
            binding.settingsPageContainer.addView(panelView,
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
