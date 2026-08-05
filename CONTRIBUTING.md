# Building

When cloning the repository, please clone submodules recursively.

```agsl
git clone --recurse-submodules <url>
```

If any weird errors show up with modules, updating submodules may solve your issue. media and taglib are required
modules. ffMetadataEx is required for only the "full" build variant

```bash
git submodule update --init --recursive
```

For most users, we recommend importing and building through Android Studio.

## Build flavors/variants

There are the following build flavours

```
core
full
```

| Flavour | Architecture support                | Version update checker | FFmpeg metadata tag extractor | FFmpeg audio decoders | 
|---------|-------------------------------------|------------------------|-------------------------------|-----------------------|
| core    | arm64-v8a, armeabi-v7a, x86, x86_64 | ❌                      | ❌                             | ❌                     |
| full    | arm64-v8a, armeabi-v7a, x86, x86_64 | ✅                      | ✅                             | ✅                     |

**Extra setup is required for the `full` variant.** Please
see https://github.com/OuterTune/ffMetadataEx/blob/main/README.md#building

<br/><br/>

# Contributing to OuterTune

## Translations

当前二次开发版本只维护简体中文界面。新增或修改用户可见文案时，请同时检查
`app/src/main/res/values` 与 `app/src/main/res/values-zh-rCN`。

### Important

- 新功能文案放入 `strings-ot.xml`
- 保持默认资源与 `zh-rCN` 资源键一致
- 不新增其他语言资源目录

## Submitting a pull request

To make everyone's life easier, there are a set of guidelines that are to be followed when submitting pull requests.

- One pull request for one feature/issue, please refrain from tackling many features/issues in one pull request
- Write a descriptive title and a meaningful description
- Upload images/video for any UI changes
- In the event of merge conflicts, you may be required to rebase onto the current `dev` branch
- **You are required to build and test the app before submitting a pull request**

## Commiting guidelines

- Prefix commits with tags, and provide descriptions if necessary. These are generally done in format:
  `tag: commit_name`. [Example](https://github.com/OuterTune/OuterTune/commit/798e8366227dd2cc38355224c733dbf7e8ffcee0)
    - A list of tags commonly used is provided below
- Commit descriptions are not required, but highly recommended
- When porting/cherry-picking/stealing from other repositories or sources:
    - Maintain
      authorship. [Example](https://github.com/OuterTune/OuterTune/commit/b0dc59682190b41f0200e9df5174322acaa3d40d)
    - If this is not possible please provide the source in the commit
      description. [Example](https://github.com/OuterTune/OuterTune/pull/59/commits/e40325dd86ac2c30347cfd4f9e92bbf15a0d0c82)
- IMPORTANT: When merging `dev` into your branch
    - Best: Please rebase over dev
    - Less good: Merging dev into your branch via merge commit. We do not like this as it clobbers the commit history.
      If you do this, your pull request will be rebased/squashed and merged
- IMPORTANT: Merge conflicts
    - As per the previous point, please rebase and conflicts are to be resolve in the commits themselves
    - We may ask you to rebase your PR if merge conflicts are an issue when merging
    - In the event you do not wish to rebase, your pull request will be squashed and merged as stated above
- If database schema changes are required, please state clearly if a version increment is required. Additional details
  are in the `Database schema changes` section
- For multi-part commits where all parts are required for functionality, use
  `[1/2], [2/2], etc`. [See example](https://github.com/OuterTune/OuterTune/pull/59/commits)
- Do not mess with translation files directly, this is managed with Weblate.

### Tags

| Tag (General) | Description                                                                                         |
|---------------|-----------------------------------------------------------------------------------------------------|
| github        | GitHub facing configs, ex. build scripts, templates, etc.                                           |
| gradle        | Dependency/library updates                                                                          |
| readme        | Readme changes                                                                                      |
| fixup         | Amendments to certain commits. this is generally done in the format `fixup: <old commit name here>` |

| Tag (ui) | Description                                                                        |
|----------|------------------------------------------------------------------------------------|
| ui       | User interface                                                                     |
| library  | Library general components. Use the specific ones below if it is a specific change |
| artist   | Playlist screens & components                                                      |
| album    | Album screens & components                                                         |
| playlist | Playlist screens & components                                                      |
| songs    | Song screens & components                                                          |

| Tag (Playback) | Description               |
|----------------|---------------------------|
| player         | Music playback components | 
| multiqueue     | Queue components          |

| Tag (Features) | Description                 |
|----------------|-----------------------------|
| downloads      | Offline song downloads      |
| source         | 洛雪音源、在线搜索和解析链路 |

| Tag (Local Media) | Description                      |
|-------------------|----------------------------------|
| lm                | General local library components | 
| scanner           | Local media scanner              |

| Tag (Misc)    | Description                                                                                     |
|---------------|-------------------------------------------------------------------------------------------------|
| `<file name>` | Changes for one single file, that to not fit into any other tags                                |
| app           | General changes, or anything that does not fit any other tags. (For within the app module only) |
| outertune     | General changes that span across multiple modules                                               |
| fastlane      | Fastlane components                                                                             |
| translations  | General changes to translation components                                                       |

- Please use a tag if it already exists, however, if you are developing a new major feature, you are free to assign your
  own appropriate tag
- Tags can be stacked. For example: `ui: library: Something something`

## Database schema changes

- Clearly state if a database version increment is required
- You are require to make sure migration works from the previous database version
- Commits modifying the database version should be all be contained in that single commit
    - These include generating JSON, Entity classes, migration conflict resolution, bumping database version etc.
