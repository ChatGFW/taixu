#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    if std::env::args().any(|arg| arg == "--version") {
        println!("CC Switch 4.0.30 (TaiXu Web adapter)");
        return Ok(());
    }
    cc_switch_lib::web::run().await
}
