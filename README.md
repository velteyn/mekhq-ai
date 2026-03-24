# MekHQ

## Table of Contents

1. [About](#about)
2. [Status](#status)
3. [Compiling](#compiling)
4. [Support](#support)
5. [License](#licensing)

## About

MekHQ is a Java helper program for the [MegaMek](http://megamek.org) game that allows users to run a campaign. For more
details, see their site [website](http://megamek.org/).

This fork is a mod providing AI LLM lore generation for the people wanting to play but that have very short time (like me).
This fork make extensive use of AI generation but attempt to remain in sync with the original project. Since the maintainers decided that this contribution is not interesting I will continue here: no pull requests will be asked again. The branch for LLM is "ai-support" , the "main" branch is used to keep it in sync with the original MekHQ project.
To use AI Features you must install LM Studio (free) and download  Mistral 7b instruct. This is the only supported large language mode for now. 
I created also a [AI MANUAL](AI_FEATURES_MANUAL.md) for you to read.

## Compiling

1) Install [Gradle](https://gradle.org/).

2) Follow the [instructions on the wiki](https://github.com/MegaMek/megamek/wiki/Working-With-Gradle) for using Gradle.

### 3.1 Style Guide

When contributing to this project, please enable the EditorConfig option within your IDE to ensure some basic compliance
with our [style guide](https://github.com/MegaMek/megamek/wiki/MegaMek-Coding-Style-Guide) which includes some defaults
for line length, tabs vs spaces, etc. When all else fails, we follow
the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).

The first ensures compliance with with the EditorConfig file, the other works with the Google Style Guide for most of
the rest.

## Support

For bugs, crashes, or other issues you can fill out a [GitHub issue request](https://github.com/MegaMek/mekhq/issues).

## Licensing

MekHQ is licensed under a dual-licensing approach:

### Code License

All source code is licensed under the GNU General Public License v3.0 (GPLv3). See the [LICENSE.code](LICENSE.code) file
for details.

### Data/Assets License

Game data, artwork, and other non-code assets are licensed under the Creative Commons Attribution-NonCommercial 4.0
International License (CC-BY-NC-4.0). See the [LICENSE.assets](LICENSE.assets) file for details.

### BattleTech IP Notice

MechWarrior, BattleMech, `Mech, and AeroTech are registered trademarks of The Topps Company, Inc. All Rights Reserved.
Catalyst Game Labs and the Catalyst Game Labs logo are trademarks of InMediaRes Productions, LLC.

The BattleTech name for electronic games is a trademark of Microsoft Corporation.

MegaMek is an unofficial, fan-created digital adaptation and is not affiliated with, endorsed by, or licensed by
Microsoft Corporation, The Topps Company, Inc., or Catalyst Game Labs.

### Full Licensing Details

For complete information about licensing, including specific directories and files, please see the [LICENSE](LICENSE)
document.
