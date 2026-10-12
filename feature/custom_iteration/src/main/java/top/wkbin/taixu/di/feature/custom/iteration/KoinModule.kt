package top.wkbin.taixu.di.feature.custom.iteration

import org.koin.dsl.module
import top.wkbin.taixu.ui.iteration.CustomIterationViewModel
import top.wkbin.taixu.iteration.engine.TaiXuDevBuildCoordinator
import top.wkbin.taixu.runtime.LinuxRuntime
import org.koin.core.module.dsl.viewModel

/** Dependency registrations owned by the feature:custom_iteration module. */
val featureCustomIterationModule = module {
    // CI 构建协调器：ShellRunner 绑定 LinuxRuntime 标准契约，在沙盒内执行 gh 命令
    factory<TaiXuDevBuildCoordinator> {
        TaiXuDevBuildCoordinator(
            shellRunner = { command -> get<LinuxRuntime>().execute(command) },
        )
    }

    viewModel<CustomIterationViewModel> {
        CustomIterationViewModel(application = get(), buildCoordinator = get())
    }
}
