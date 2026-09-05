# -*- coding: utf-8 -*-
"""MainActivity 玻璃方法改为 GlassDialogFactory 一行委托。"""
import io

p = 'app/src/main/java/com/polaris/timetable/MainActivity.java'
with io.open(p, encoding='utf-8', newline='') as f:
    lines = f.read().split('\n')

SIGS = [
    ('private View glassDialogContent(LinearLayout panel, int radius, int opacityPercent) {',
     ['    return GlassDialogFactory.dialogContent(panel, glassConfig(),',
      '            dialogBlurSource(), radius, opacityPercent);']),
    ('private View glassDialogContent(ScrollView scrollView, LinearLayout panel, int radius) {',
     ['    return GlassDialogFactory.dialogContent(scrollView, glassConfig(),',
      '            dialogBlurSource(), radius);']),
    ('private GradientDrawable dialogGlassBg(int radius) {',
     ['    return GlassDialogFactory.dialogGlassBg(glassConfig(), radius);']),
    ('private GradientDrawable dialogGlassBg(int radius, int opacityPercent) {',
     ['    return GlassDialogFactory.dialogGlassBg(glassConfig(), radius, opacityPercent);']),
    ('private GradientDrawable liquidGlassBg(int opacityPercent) {',
     ['    return GlassDialogFactory.liquidGlassBg(glassConfig(), opacityPercent);']),
    ('private View glassLayer(GradientDrawable background, int radius) {',
     ['    return GlassDialogFactory.glassLayer(glassConfig(), contentHost, background, radius);']),
    ('private void updateGlassLayer(View layer, GradientDrawable background, int radius) {',
     ['    GlassDialogFactory.updateGlassLayer(layer, glassConfig(), contentHost, background, radius);']),
    ('private GradientDrawable floatingPanelBg(int opacityPercent, int radius) {',
     ['    return GlassDialogFactory.floatingPanelBg(glassConfig(), opacityPercent, radius);']),
]

replaced = 0
for sig, body in SIGS:
    start = None
    for i, line in enumerate(lines):
        if line.strip() == sig:
            start = i
            break
    assert start is not None, sig
    end = start + 1
    while lines[end] != '    }':
        end += 1
    lines[start:end + 1] = [sig.replace(' {', ' {')] + body + ['    }']
    replaced += 1
print('delegates:', replaced)
assert replaced == 8

out = '\n'.join(lines)
anchor = '''    private View dialogBlurSource() {
        if (!shellBarsBlurEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return null;
        }
        return rootView != null ? rootView : getWindow().getDecorView();
    }
'''
assert out.count(anchor) == 1
out = out.replace(anchor, anchor + '''
    private GlassDialogFactory.Config glassConfig() {
        return new GlassDialogFactory.Config(this, shellBarsBlurEnabled, isDarkModeActive(),
                isMinimalVisualTheme(), visualTheme);
    }
''')
old_imp = 'import com.polaris.timetable.ui.CourseConflictSummaryView;'
assert out.count(old_imp) == 1
out = out.replace(old_imp, old_imp + '\nimport com.polaris.timetable.ui.dialog.GlassDialogFactory;')
io.open(p, 'w', encoding='utf-8', newline='').write(out)
print('OK: 委托改造完成')
