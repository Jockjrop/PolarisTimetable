# -*- coding: utf-8 -*-
"""MainActivity 我的页/账户弹窗簇文案资源化:行级精确替换。"""
import io, sys

PATH = 'MainActivity.java'
with io.open(PATH, encoding='utf-8', newline='') as f:
    lines = f.read().split('\n')

R = [
    (2753, 2753, 'Toast.makeText(this, "无法预览所选图片，请重新选择"', 'Toast.makeText(this, getString(R.string.profile_preview_image_failed)'),
    (2757, 2757, 'Toast.makeText(this, "图片尺寸过大，无法作为课表背景"', 'Toast.makeText(this, getString(R.string.profile_background_too_large)'),
    (2762, 2762, 'Toast.makeText(this, "无法预览所选图片，请重新选择"', 'Toast.makeText(this, getString(R.string.profile_preview_image_failed)'),
    (2766, 2766, 'dialogPanel("调整展示区域")', 'dialogPanel(getString(R.string.profile_crop_area_title))'),
    (2769, 2769, 'instruction.setText("单指拖动图片，双指缩放；白色框内将作为课表背景");', 'instruction.setText(getString(R.string.profile_crop_instruction));'),
    (2791, 2791, 'use.setText("应用此展示区域");', 'use.setText(getString(R.string.profile_apply_area));'),
    (2792, 2792, 'use.setContentDescription("应用当前背景展示区域");', 'use.setContentDescription(getString(R.string.profile_apply_area_cd));'),
    (2805, 2805, 'chooseAgain.setText("重新选择");', 'chooseAgain.setText(getString(R.string.profile_choose_again));'),
    (2837, 2837, 'dialogPanel("编辑账户资料")', 'dialogPanel(getString(R.string.profile_editor_title))'),
    (2843, 2843, 'accountAvatarPreview.setContentDescription("选择并裁剪头像");', 'accountAvatarPreview.setContentDescription(getString(R.string.profile_choose_avatar_cd));'),
    (2852, 2852, 'avatarHint.setText("点击头像可从相册选择并裁剪");', 'avatarHint.setText(getString(R.string.profile_avatar_hint));'),
    (2861, 2861, 'input("账户名称", accountName)', 'input(getString(R.string.profile_name_hint), accountName)'),
    (2867, 2867, 'chooseAvatar.setText("从相册选择头像");', 'chooseAvatar.setText(getString(R.string.profile_choose_avatar));'),
    (2879, 2879, 'cancel.setText("取消");', 'cancel.setText(getString(R.string.editor_action_cancel));'),
    (2913, 2913, 'Toast.makeText(this, "无法预览所选头像，请重新选择"', 'Toast.makeText(this, getString(R.string.profile_avatar_preview_failed)'),
    (2917, 2917, 'Toast.makeText(this, "图片尺寸过大，无法设置为头像"', 'Toast.makeText(this, getString(R.string.profile_avatar_too_large)'),
    (2921, 2921, 'Toast.makeText(this, "无法预览所选头像，请重新选择"', 'Toast.makeText(this, getString(R.string.profile_avatar_preview_failed)'),
    (2926, 2926, 'dialogPanel("裁剪头像")', 'dialogPanel(getString(R.string.avatar_crop_title))'),
    (2929, 2929, 'instruction.setText("单指拖动，双指缩放；圆形区域将作为头像");', 'instruction.setText(getString(R.string.avatar_crop_instruction));'),
    (2939, 2939, 'cropView.setContentDescription("头像裁剪区域，可单指拖动、双指缩放");', 'cropView.setContentDescription(getString(R.string.avatar_crop_cd));'),
    (2948, 2948, 'use.setText("使用此头像");', 'use.setText(getString(R.string.avatar_use));'),
    (2973, 2973, 'cancel.setText("取消");', 'cancel.setText(getString(R.string.editor_action_cancel));'),
    (5045, 5045, 'return "Polaris课程表 v" + (versionName == null ? "" : versionName);',
     'return getString(R.string.my_version_value, versionName == null ? "" : versionName);'),
    (5047, 5047, 'return "Polaris课程表";', 'return getString(R.string.app_name);'),
    (5063, 5063, 'avatar.setContentDescription("修改账户名称和头像");', 'avatar.setContentDescription(getString(R.string.my_cd_edit_account));'),
    (5210, 5210, 'avatar.setContentDescription("修改账户名称和头像");', 'avatar.setContentDescription(getString(R.string.my_cd_edit_account));'),
    (8145, 8145, '? "管理员" : savedName;', '? "管理员" : savedName; // 默认账户名是持久化数据值,不资源化'),
]

errors = []
applied = 0
for start, end, old, new in R:
    hits = [i for i in range(start - 1, min(end, len(lines))) if old in lines[i]]
    if len(hits) != 1:
        errors.append('预期1次,实际%d次: 行%d: %s' % (len(hits), start, old[:50]))
        continue
    lines[hits[0]] = lines[hits[0]].replace(old, new)
    applied += 1

if errors:
    for e in errors:
        print('MISS:', e)
    sys.exit(1)

with io.open(PATH, 'w', encoding='utf-8', newline='') as f:
    f.write('\n'.join(lines))
print('OK: 应用 %d 条替换' % applied)
