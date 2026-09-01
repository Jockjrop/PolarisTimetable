package com.polaris.timetable.testing;

import android.view.View;
import android.widget.TextView;

import androidx.test.espresso.matcher.BoundedMatcher;

import org.hamcrest.Description;
import org.hamcrest.Matcher;

/**
 * 测试用文本匹配器补充。
 *
 * <p>底部导航标签的文本并非纯标签名：{@code MainActivity.navText} 会在标签上方加图标字形，
 * 实际形如 {@code "▣\n课表"}、{@code "✎\n计划"}、{@code "○\n我的"}；
 * 且 {@code styledNavText} 返回 {@link android.text.SpannableString}，
 * 而 Espresso 自带的 {@code ViewMatchers.withText(String)} 内部要求
 * {@code item instanceof String}，对 Spannable 永远不匹配。
 *
 * <p>因此按标签名定位导航项时，必须使用本类的 {@link #withNavLabel(String)}：
 * 它按 {@code getText().toString()} 的最后一行做等值比较，与图标字形和 Span 类型无关。
 */
public final class TextMatchers {

    private TextMatchers() {
    }

    /**
     * 匹配「最后一行文本」等于 {@code label} 的 TextView，用于定位底部导航项。
     */
    public static Matcher<View> withNavLabel(final String label) {
        return new BoundedMatcher<View, TextView>(TextView.class) {
            @Override
            public void describeTo(Description description) {
                description.appendText("nav label: \"" + label + "\"");
            }

            @Override
            protected boolean matchesSafely(TextView view) {
                return label.equals(lastLine(view.getText()));
            }
        };
    }

    private static String lastLine(CharSequence text) {
        if (text == null) {
            return null;
        }
        String value = text.toString();
        int index = value.lastIndexOf('\n');
        return index < 0 ? value : value.substring(index + 1);
    }
}
