use grammers_client::client::SignInError;
use grammers_client::update::Update;
use grammers_client::Client;
use grammers_mtsender::SenderPool;
use yumigram_core::session::FileSession;
use std::env;
use std::io::{self, BufRead, Write};
use std::sync::Arc;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    println!("=========================================");
    println!("   🚀 Yumigram Core — Тестовий запуск    ");
    println!("=========================================");

    let api_id: i32 = match env::var("TG_API_ID") {
        Ok(val) => val.parse()?,
        Err(_) => {
            print!("Введіть ваш API ID: ");
            io::stdout().flush()?;
            let mut input = String::new();
            io::stdin().lock().read_line(&mut input)?;
            input.trim().parse()?
        }
    };

    let api_hash = match env::var("TG_API_HASH") {
        Ok(val) => val,
        Err(_) => {
            print!("Введіть ваш API HASH: ");
            io::stdout().flush()?;
            let mut input = String::new();
            io::stdin().lock().read_line(&mut input)?;
            input.trim().to_string()
        }
    };

    println!("\n[1/3] Підключення до серверів Telegram (MTProto 2.0)...");
    let session_file = "yumigram_test.session";
    let session = Arc::new(FileSession::load_or_create(session_file));
    let pool = SenderPool::new(session, api_id);
    let updates_rx = pool.updates;
    let client = Client::new(pool.handle);

    println!("[2/3] З'єднання встановлено!");

    if !client.is_authorized().await? {
        print!("Введіть номер телефону (наприклад +380...): ");
        io::stdout().flush()?;
        let mut phone = String::new();
        io::stdin().lock().read_line(&mut phone)?;
        let phone = phone.trim();

        println!("Запит коду підтвердження...");
        let token = client.request_login_code(phone, &api_hash).await?;

        print!("Введіть код, який прийшов у Telegram: ");
        io::stdout().flush()?;
        let mut code = String::new();
        io::stdin().lock().read_line(&mut code)?;
        let code = code.trim();

        match client.sign_in(&token, code).await {
            Ok(user) => {
                let name = user.first_name().unwrap_or("Користувач");
                println!("✅ Авторизація успішна! Ласкаво просимо, {}!", name);
            }
            Err(SignInError::PasswordRequired(password_token)) => {
                print!("Введіть ваш 2FA хмарний пароль: ");
                io::stdout().flush()?;
                let mut password = String::new();
                io::stdin().lock().read_line(&mut password)?;
                let password = password.trim();

                client.check_password(password_token, password).await?;
                println!("✅ 2FA пароль прийнято!");
            }
            Err(e) => {
                eprintln!("❌ Помилка входу: {:?}", e);
                return Ok(());
            }
        }
    } else {
        println!("✅ Ви вже авторизовані через файл сесії!");
    }

    println!("\n[3/3] Ваші останні 5 чатів:");
    let mut dialogs = client.iter_dialogs();
    let mut count = 0;
    while let Some(dialog) = dialogs.next().await? {
        count += 1;
        let title = dialog.peer.name().unwrap_or("Без назви");
        println!("  {}. 💬 {} (ID: {:?})", count, title, dialog.peer.id());
        if count >= 5 {
            break;
        }
    }

    println!("\n-----------------------------------------------------------");
    println!("🔴 Запуск фонового прослуховування оновлень у реальному часі!");
    println!("   (Спробуйте написати, відредагувати або видалити повідомлення з іншого пристрою)");
    println!("   Натисніть Ctrl+C для зупинки.");
    println!("-----------------------------------------------------------");

    let mut stream = client.stream_updates(updates_rx, Default::default()).await?;
    while let Ok(update) = stream.next().await {
        match update {
            Update::NewMessage(msg) => {
                let sender = msg.sender().and_then(|p| p.name()).unwrap_or("Співрозмовник");
                println!("📩 [НОВЕ] від {}: \"{}\"", sender, msg.text());
            }
            Update::MessageEdited(msg) => {
                println!("✏️ [ВІДРЕДАГОВАНО] ID {}: нова версія -> \"{}\"", msg.id(), msg.text());
            }
            Update::MessageDeleted(del) => {
                println!("🗑️ [ANTI-DELETE СПІЙМАНО!] Видалено повідомлення ID: {:?}", del.messages());
            }
            _ => {}
        }
    }

    Ok(())
}
