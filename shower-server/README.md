# shower-server（直出 Surface，无 H.264）

```
VirtualDisplay ──► 客户端 SurfaceView（预览）
             ↘ 无客户端时挂 ImageReader 保活
```

`app_process` 进程经 Binder 把 `IShowerService` 交给宿主；建屏、注入、截图均在此完成。
