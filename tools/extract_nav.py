# -*- coding: utf-8 -*-
"""MainActivity 接入 BottomNavView:实现 Host 接口,删除搬走的构建方法。"""
import io

p = 'app/src/main/java/com/polaris/timetable/MainActivity.java'
with io.open(p, encoding='utf-8', newline='') as f:
    s = f.read()

def replace_once(old, new):
    global s
    assert s.count(old) == 1, '非唯一或缺失: %s' % old[:60]
    s = s.replace(old, new)

# 1) 类声明实现 Host 接口
replace_once('public class MainActivity extends Activity {',
             'public class MainActivity extends Activity implements BottomNavView.Host {')

# 2) 字段类型与删除三个入口字段
replace_once('private LinearLayout bottomNavView;',
             'private BottomNavView bottomNavView;')
replace_once('''    private TextView scheduleNav;
    private TextView planNav;
    private TextView myNav;
''', '')

# 3) bottomNav() 改为构建 BottomNavView;删除 tabletBottomBar/navItem
start = s.index('    private LinearLayout bottomNav() {')
end_marker = '    private TextView navItem(String text, boolean active, int tab) {'
nav_item_start = s.index(end_marker)
nav_item_end = s.index('    }', s.index('return item;', nav_item_start)) + len('    }')
new_bottom = '''    private LinearLayout bottomNav() {
        bottomNavView = new BottomNavView(this);
        return bottomNavView;
    }
'''
s = s[:start] + new_bottom + s[nav_item_end:]
# 清理 tabletBottomBar(现在位于 bottomNav 之后残留)
if 'private LinearLayout tabletBottomBar() {' in s:
    # tabletBottomBar 结构:方法体最后是 addView(container...) 与 "}",向后找方法结束
    tb_method = s.index('    private LinearLayout tabletBottomBar() {')
    end = s.index('\n    }\n', tb_method) + len('\n    }\n')
    s = s[:tb_method] + s[end:]

# 4) switchTab 三个入口块替换
old_switch = '''        if (scheduleNav != null) {
            scheduleNav.setText(styledNavText(navText("课表", schedule)));
            scheduleNav.setTypeface(schedule ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            scheduleNav.setTextColor(schedule ? inkColor() : mutedColor());
        }
        if (planNav != null) {
            planNav.setText(styledNavText(navText("计划", plan)));
            planNav.setTypeface(plan ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            planNav.setTextColor(plan ? inkColor() : mutedColor());
        }
        if (myNav != null) {
            myNav.setText(styledNavText(navText("我的", mine)));
            myNav.setTypeface(mine ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            myNav.setTextColor(mine ? inkColor() : mutedColor());
        }'''
new_switch = '''        if (bottomNavView != null) {
            bottomNavView.updateTabs(schedule, plan, mine);
        }'''
replace_once(old_switch, new_switch)

# 5) applyShellAppearance 颜色块替换(保持原 activeTab==0/==1 行为)
old_color = '''        if (scheduleNav != null) {
            boolean active = activeTab == 0;
            scheduleNav.setTextColor(active ? inkColor() : mutedColor());
        }
        if (myNav != null) {
            boolean active = activeTab == 1;
            myNav.setTextColor(active ? inkColor() : mutedColor());
        }'''
new_color = '''        if (bottomNavView != null) {
            bottomNavView.applyTabColors(activeTab == 0, activeTab == 1);
        }'''
replace_once(old_color, new_color)

# 6) Host 接口实现(追加在 navText 方法之后)
anchor = '''        if ("课表".equals(label)) {
            return (active ? "▣" : "▦") + "\\n" + label;
        }
        if ("计划".equals(label)) {
            return "✎\\n" + label;
        }
        return (active ? "●" : "○") + "\\n" + label;
    }'''
assert s.count(anchor) == 1
host_impl = anchor + '''

    @Override
    public boolean navDarkMode() {
        return isDarkModeActive();
    }

    @Override
    public boolean navMinimalTheme() {
        return isMinimalVisualTheme();
    }

    @Override
    public String navVisualTheme() {
        return visualTheme;
    }

    @Override
    public boolean navBlurEnabled() {
        return shellBarsBlurEnabled;
    }

    @Override
    public int navOpacity() {
        return bottomNavOpacity;
    }

    @Override
    public int navBottomInset() {
        return Math.max(systemBottomInset, 0);
    }

    @Override
    public int navInkColor() {
        return inkColor();
    }

    @Override
    public int navMutedColor() {
        return mutedColor();
    }

    @Override
    public boolean navTabActive(int tab) {
        return activeTab == tab;
    }

    @Override
    public void onNavTabSelected(int tab) {
        switchTab(tab);
    }

    @Override
    public View navContentSource() {
        return contentHost;
    }

    @Override
    public CharSequence navLabel(int tab, boolean active) {
        String label = tab == 0 ? "课表" : tab == 1 ? "计划" : "我的";
        return styledNavText(navText(label, active));
    }

    @Override
    public void attachNavPressFeedback(View item) {
        attachPressFeedback(item);
    }'''
s = s.replace(anchor, host_impl)

# 7) 导入
old_imp = 'import com.polaris.timetable.ui.TodayOverviewView;'
assert s.count(old_imp) == 1
s = s.replace(old_imp, old_imp + '\nimport com.polaris.timetable.ui.shell.BottomNavView;')

io.open(p, 'w', encoding='utf-8', newline='').write(s)
print('OK: BottomNavView 接入完成')
