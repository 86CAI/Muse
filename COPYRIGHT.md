# COPYRIGHT / 版权与许可声明

Muse — Android music player
Copyright (C) 2026 Cai & Caiyu (86CAI)

This program is free software: you can redistribute it and/or modify it under
the terms of the GNU Affero General Public License as published by the Free
Software Foundation, either version 3 of the License, or (at your option) any
later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License along
with this program. If not, see <https://www.gnu.org/licenses/>.

Source code: https://github.com/86CAI/Muse

---

## 为什么是 AGPL-3.0 / Why AGPL-3.0

Muse 的界面移植、改编了多个 copyleft 项目的代码：

- zyrouge/Symphony — **AGPL-3.0-only**
- NEORUAA/Mei_MeloX_Android — GPL-3.0
- lladlam/MeloX-Android — GPL-3.0
- XxHuberrr/Mineradio — GPL-3.0

AGPL-3.0 是这些条款中最严格的一项，且 AGPL-3.0 第 13 节明确允许把 GPL-3.0 的
代码合并进 AGPL-3.0 作品。因此 Muse 整体只能以 AGPL-3.0 分发；选择更宽松的许可证
（MIT / Apache-2.0）会违反上游条款。

Muse's UI adapts code from several copyleft projects; Symphony is AGPL-3.0-only,
which is the strictest term in the set, and AGPL-3.0 section 13 explicitly permits
combining GPL-3.0 code into an AGPL-3.0 work. The combined work therefore has to
be conveyed under AGPL-3.0.

## 分发时的义务 / Obligations when distributing

1. **提供源代码。** 分发 APK 时必须同时提供与该二进制对应的完整源代码，或提供
   有效的书面获取方式（AGPL-3.0 §6）。
2. **网络交互条款。** Muse 内置 Open API 服务与局域网遥控。若你修改 Muse 并让
   用户通过网络与其交互，必须向这些用户提供你修改版本的源代码（AGPL-3.0 §13）。
3. **保留声明。** 不得移除源文件中的版权与来源注释，也不得移除应用内
   「关于 → 开源许可」页面。
4. **附带许可证副本。** `licenses/` 目录下的许可证全文须随分发一并提供；
   构建产物已把它们打包到 `assets/licenses/`。
5. **标注修改。** 修改版本须注明已被修改及修改日期（AGPL-3.0 §5a）。

## 不受本许可证覆盖的部分 / Not covered by this license

以下内容按其各自条款授权，AGPL-3.0 不适用，也不因随 Muse 分发而转为 AGPL：

- 第三方库（Apache-2.0 / MPL-2.0 / BSD-3-Clause / ISC）——见 `THIRD_PARTY_NOTICES.md`
- `app/src/main/res/font/sf_pro.ttf`（Apple SF Pro）与 SF Symbols 字形——**Apple
  专有授权**。Apple 的条款禁止将该字体嵌入软件产品分发，公开发布前应替换。
- 用户自行导入的音源脚本、皮肤与插件——版权归其各自作者。
- Muse 名称与图标——保留权利，再分发的修改版本请使用不同的名称与图标，
  以免让用户误认为是官方版本。

完整的第三方来源、许可证与落地位置清单见 `THIRD_PARTY_NOTICES.md`；
应用内亦可在「设置 → 关于 → 开源许可」查看。
