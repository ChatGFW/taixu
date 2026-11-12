use heck::ToLowerCamelCase;
use proc_macro::TokenStream;
use quote::{format_ident, quote, ToTokens};
use syn::{parse_macro_input, FnArg, ItemFn, Pat, Type};

#[proc_macro_attribute]
pub fn command(_attr: TokenStream, input: TokenStream) -> TokenStream {
    let mut function = parse_macro_input!(input as ItemFn);
    let name = function.sig.ident.clone();
    let wrapper = format_ident!("__web_{}", name);
    function.sig.generics = Default::default();
    let body: String = function.block.to_token_stream().to_string().chars().filter(|c| !c.is_whitespace()).collect();
    let desktop = [".dialog(", ".opener(", "tauri_plugin_", ".updater_builder(",
        "crate::lightweight", "crate::auto_launch", "crate::restart_process",
        "crate::save_window_state", "tauri::Theme", "arboard::",
        "get_webview_window", "set_app_config_dir_to_store"]
        .iter().any(|marker| body.contains(marker));
    if name == "is_lightweight_mode" {
        function.block = Box::new(syn::parse_quote!({ false }));
    } else if desktop {
        function.block = Box::new(syn::parse_quote!({
            return Err("此功能依赖桌面环境；Web 插件不支持该操作".into());
        }));
    }
    let mut arguments = Vec::new();
    for argument in &mut function.sig.inputs {
        if let FnArg::Typed(arg) = argument {
            arg.attrs.clear();
            if arg.ty.to_token_stream().to_string().contains("AppHandle <") {
                arg.ty = Box::new(syn::parse_quote!(tauri::AppHandle));
            }
            let Pat::Ident(pat) = arg.pat.as_ref() else { panic!("unsupported argument") };
            let key = pat.ident.to_string().to_lower_camel_case();
            let ty = &arg.ty;
            let text = ty.to_token_stream().to_string();
            if text.contains("AppHandle") {
                arguments.push(quote!(app.clone()));
            } else if text.contains("State <") {
                arguments.push(quote!(app.try_state().ok_or_else(|| "Service state unavailable".to_string())?));
            } else if text.contains("Window") {
                arguments.push(quote!(tauri::Window));
            } else {
                let _ = matches!(ty.as_ref(), Type::Path(_));
                arguments.push(quote!(tauri::serde_json::from_value(args.get(#key).cloned().unwrap_or(tauri::serde_json::Value::Null))
                    .map_err(|_| format!("Invalid argument: {}", #key))?));
            }
        }
    }
    let call = if function.sig.asyncness.is_some() { quote!(#name(#(#arguments),*).await) } else { quote!(#name(#(#arguments),*)) };
    let result = if function.sig.output.to_token_stream().to_string().contains("Result") {
        quote!(let result = #call.map_err(|error| tauri::serde_json::to_value(error).unwrap_or(tauri::serde_json::Value::String("Command failed".into())))?;)
    } else { quote!(let result = #call;) };
    let execution = quote! {
        #result
        tauri::serde_json::to_value(result).map_err(|error| tauri::serde_json::Value::String(error.to_string()))
    };
    let execution = if function.sig.asyncness.is_none() {
        quote! { tauri::async_runtime::spawn_blocking(move || { #execution }).await.map_err(|_| tauri::serde_json::Value::String("Command task failed".into()))? }
    } else { execution };
    quote! {
        #function
        fn #wrapper(app: tauri::AppHandle, args: tauri::serde_json::Value) -> tauri::CommandFuture {
            Box::pin(async move {
                #execution
            })
        }
        tauri::inventory::submit! { tauri::Command { name: stringify!(#name), run: #wrapper } }
    }.into()
}
