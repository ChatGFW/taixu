package top.wkbin.taixu.di.runtime

import org.koin.dsl.module
import top.wkbin.taixu.runtime.RuntimePathManager
import top.wkbin.taixu.runtime.rootfs.AssetBundledRootfsSource
import top.wkbin.taixu.runtime.rootfs.BundledRootfsProvisioner
import top.wkbin.taixu.runtime.rootfs.BundledRootfsSource
import top.wkbin.taixu.runtime.rootfs.LxcImagesClient
import top.wkbin.taixu.runtime.rootfs.OciRegistryClient
import top.wkbin.taixu.runtime.rootfs.RootfsInstaller
import top.wkbin.taixu.runtime.rootfs.RootfsValidator

/** RootFS 安装相关依赖：自 runtimeModule 拆出以控制单文件行数。 */
val rootfsModule = module {
    single<LxcImagesClient> { LxcImagesClient(http = get(), logger = get()) }
    single<OciRegistryClient> { OciRegistryClient(http = get(), logger = get()) }
    single<RootfsValidator> { RootfsValidator(elfInspector = get()) }
    single<RootfsInstaller> {
        RootfsInstaller(
            pathManager = get(),
            tarStreamExtractor = get(),
            rootfsValidator = get(),
            logger = get(),
            ociRegistryClient = get(),
            lxcImagesClient = get(),
        )
    }
    single<BundledRootfsSource> { AssetBundledRootfsSource(context = get(), logger = get()) }
    single<BundledRootfsProvisioner> {
        BundledRootfsProvisioner(
            rootfsInstaller = get(),
            source = get(),
            cacheDir = get<RuntimePathManager>().cacheDir,
            logger = get(),
        )
    }
}
