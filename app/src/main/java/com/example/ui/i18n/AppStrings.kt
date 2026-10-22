package com.example.ui.i18n

object AppStrings {

    // --- BOTTOM NAVIGATION ---
    fun navItems(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "आइटम सूची"
        AppLanguage.SPANISH -> "Artículos"
        AppLanguage.CHINESE -> "商品列表"
        AppLanguage.RUSSIAN -> "Товары"
        else -> "Items"
    }

    fun navCustomers(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "ग्राहक खाता"
        AppLanguage.SPANISH -> "Clientes"
        AppLanguage.CHINESE -> "客户账本"
        AppLanguage.RUSSIAN -> "Клиенты"
        else -> "Customers"
    }

    fun navInvoices(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "रसीदें"
        AppLanguage.SPANISH -> "Facturas"
        AppLanguage.CHINESE -> "收据账单"
        AppLanguage.RUSSIAN -> "Счета"
        else -> "Invoices"
    }

    fun navSettings(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "सेटिंग्स"
        AppLanguage.SPANISH -> "Ajustes"
        AppLanguage.CHINESE -> "设置"
        AppLanguage.RUSSIAN -> "Настройки"
        else -> "Settings"
    }

    // --- COMMON BUTTONS & ACTIONS ---
    fun save(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "सुरक्षित करें"
        AppLanguage.SPANISH -> "Guardar"
        AppLanguage.CHINESE -> "保存"
        AppLanguage.RUSSIAN -> "Сохранить"
        else -> "Save"
    }

    fun cancel(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "रद्द करें"
        AppLanguage.SPANISH -> "Cancelar"
        AppLanguage.CHINESE -> "取消"
        AppLanguage.RUSSIAN -> "Отмена"
        else -> "Cancel"
    }

    fun delete(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "हटाएं"
        AppLanguage.SPANISH -> "Eliminar"
        AppLanguage.CHINESE -> "删除"
        AppLanguage.RUSSIAN -> "Удалить"
        else -> "Delete"
    }

    fun deleteAll(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "सभी हटाएं"
        AppLanguage.SPANISH -> "Eliminar todo"
        AppLanguage.CHINESE -> "全部清空"
        AppLanguage.RUSSIAN -> "Удалить всё"
        else -> "Delete All"
    }

    fun restore(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "पुनर्स्थापित करें"
        AppLanguage.SPANISH -> "Restaurar"
        AppLanguage.CHINESE -> "恢复"
        AppLanguage.RUSSIAN -> "Восстановить"
        else -> "Restore"
    }

    fun done(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "पूर्ण"
        AppLanguage.SPANISH -> "Listo"
        AppLanguage.CHINESE -> "完成"
        AppLanguage.RUSSIAN -> "Готово"
        else -> "Done"
    }

    fun close(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "बंद करें"
        AppLanguage.SPANISH -> "Cerrar"
        AppLanguage.CHINESE -> "关闭"
        AppLanguage.RUSSIAN -> "Закрыть"
        else -> "Close"
    }

    fun edit(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "संपादित करें"
        AppLanguage.SPANISH -> "Editar"
        AppLanguage.CHINESE -> "编辑"
        AppLanguage.RUSSIAN -> "Изменить"
        else -> "Edit"
    }

    fun add(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "जोड़ें"
        AppLanguage.SPANISH -> "Añadir"
        AppLanguage.CHINESE -> "添加"
        AppLanguage.RUSSIAN -> "Добавить"
        else -> "Add"
    }

    fun export(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "निर्यात करें"
        AppLanguage.SPANISH -> "Exportar"
        AppLanguage.CHINESE -> "导出"
        AppLanguage.RUSSIAN -> "Экспорт"
        else -> "Export"
    }

    fun search(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "खोजें..."
        AppLanguage.SPANISH -> "Buscar..."
        AppLanguage.CHINESE -> "搜索..."
        AppLanguage.RUSSIAN -> "Поиск..."
        else -> "Search..."
    }

    // --- QUICK ACTIONS & FAB ---
    fun addSale(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "बिक्री जोड़ें (+)"
        AppLanguage.SPANISH -> "Añadir Venta"
        AppLanguage.CHINESE -> "添加销售"
        AppLanguage.RUSSIAN -> "Добавить продажу"
        else -> "Add Sale"
    }

    fun addDeposit(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "जमा जोड़ें (-)"
        AppLanguage.SPANISH -> "Añadir Cobro"
        AppLanguage.CHINESE -> "添加付款"
        AppLanguage.RUSSIAN -> "Добавить платёж"
        else -> "Add Deposit"
    }

    fun addCustomer(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "नया ग्राहक"
        AppLanguage.SPANISH -> "Nuevo Cliente"
        AppLanguage.CHINESE -> "添加客户"
        AppLanguage.RUSSIAN -> "Новый клиент"
        else -> "Add Customer"
    }

    fun addItem(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "नया आइटम"
        AppLanguage.SPANISH -> "Nuevo Artículo"
        AppLanguage.CHINESE -> "添加商品"
        AppLanguage.RUSSIAN -> "Новый товар"
        else -> "Add Item"
    }

    // --- CUSTOMERS & LEDGER SCREEN ---
    fun all(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "सभी"
        AppLanguage.SPANISH -> "TODOS"
        AppLanguage.CHINESE -> "全部"
        AppLanguage.RUSSIAN -> "ВСЕ"
        else -> "ALL"
    }

    fun allCustomers(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "सभी ग्राहक"
        AppLanguage.SPANISH -> "Todos los clientes"
        AppLanguage.CHINESE -> "全部客户"
        AppLanguage.RUSSIAN -> "Все клиенты"
        else -> "All Customers"
    }

    fun suppliers(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "आपूर्तिकर्ता"
        AppLanguage.SPANISH -> "Proveedores"
        AppLanguage.CHINESE -> "供应商"
        AppLanguage.RUSSIAN -> "Поставщики"
        else -> "Suppliers"
    }

    fun dateWise(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "दिनांक अनुसार"
        AppLanguage.SPANISH -> "Por Fecha"
        AppLanguage.CHINESE -> "按日期"
        AppLanguage.RUSSIAN -> "По дате"
        else -> "Date-wise"
    }

    fun youWillGet(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "लेना है"
        AppLanguage.SPANISH -> "Por cobrar"
        AppLanguage.CHINESE -> "应收款"
        AppLanguage.RUSSIAN -> "Вам должны"
        else -> "You'll Get"
    }

    fun youWillGive(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "देना है"
        AppLanguage.SPANISH -> "Por pagar"
        AppLanguage.CHINESE -> "应付款"
        AppLanguage.RUSSIAN -> "Вы должны"
        else -> "You'll Give"
    }

    fun customerDetails(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "ग्राहक विवरण"
        AppLanguage.SPANISH -> "Detalles del Cliente"
        AppLanguage.CHINESE -> "客户详情"
        AppLanguage.RUSSIAN -> "Детали клиента"
        else -> "Customer Details"
    }

    fun balance(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "बकाया राशि"
        AppLanguage.SPANISH -> "Saldo pendiente"
        AppLanguage.CHINESE -> "结余"
        AppLanguage.RUSSIAN -> "Баланс"
        else -> "Balance"
    }

    // --- DATES ---
    fun today(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "आज"
        AppLanguage.SPANISH -> "Hoy"
        AppLanguage.CHINESE -> "今天"
        AppLanguage.RUSSIAN -> "Сегодня"
        else -> "Today"
    }

    fun yesterday(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "कल"
        AppLanguage.SPANISH -> "Ayer"
        AppLanguage.CHINESE -> "昨天"
        AppLanguage.RUSSIAN -> "Вчера"
        else -> "Yesterday"
    }

    // --- SETTINGS CIRCULAR ACTIONS & SECTIONS ---
    fun historyAndData(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "इतिहास एवं डेटा"
        AppLanguage.SPANISH -> "Historial y Datos"
        AppLanguage.CHINESE -> "历史与数据"
        AppLanguage.RUSSIAN -> "История и Данные"
        else -> "History & Data"
    }

    fun security(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "सुरक्षा"
        AppLanguage.SPANISH -> "Seguridad"
        AppLanguage.CHINESE -> "安全"
        AppLanguage.RUSSIAN -> "Безопасность"
        else -> "Security"
    }

    fun preferencesAndLocalization(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "पसंद एवं भाषा"
        AppLanguage.SPANISH -> "Preferencias e Idioma"
        AppLanguage.CHINESE -> "偏好与本地化"
        AppLanguage.RUSSIAN -> "Настройки и Язык"
        else -> "Preferences & Localization"
    }

    fun reminders(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "याद दिलाने वाले संदेश"
        AppLanguage.SPANISH -> "Recordatorios"
        AppLanguage.CHINESE -> "提醒设置"
        AppLanguage.RUSSIAN -> "Напоминания"
        else -> "Reminders"
    }

    // 8 Circular Action Buttons
    fun actionTrash(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "हटाए गए बिल\n(Trash)"
        AppLanguage.SPANISH -> "Recibos\nBorrados"
        AppLanguage.CHINESE -> "已删账单\n回收站"
        AppLanguage.RUSSIAN -> "Корзина\nсчетов"
        else -> "Receipt\nTrash"
    }

    fun actionExport(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "खाता डेटा\nनिर्यात"
        AppLanguage.SPANISH -> "Exportar\nSaldos"
        AppLanguage.CHINESE -> "导出客户\n欠款数据"
        AppLanguage.RUSSIAN -> "Экспорт\nбаланса"
        else -> "Export\nLedger"
    }

    fun actionSync(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "क्लाउड\nसिंक"
        AppLanguage.SPANISH -> "Sincro\nNube"
        AppLanguage.CHINESE -> "自动\n同步"
        AppLanguage.RUSSIAN -> "Авто\nсинхронизация"
        else -> "Auto-Sync\nFrequency"
    }

    fun actionPasscode(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "पासकोड\nसुरक्षा"
        AppLanguage.SPANISH -> "Bloqueo\nPIN"
        AppLanguage.CHINESE -> "应用\n锁码"
        AppLanguage.RUSSIAN -> "ПИН-код\nзащита"
        else -> "Passcode\nLock"
    }

    fun actionLanguage(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "भाषा चयन\n(Language)"
        AppLanguage.SPANISH -> "Idioma\n(Language)"
        AppLanguage.CHINESE -> "语言设置\nLanguage"
        AppLanguage.RUSSIAN -> "Язык\nLanguage"
        else -> "App\nLanguage"
    }

    fun actionUnits(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "नाप-तौल\nइकाइयाँ"
        AppLanguage.SPANISH -> "Unidades de\nMedida"
        AppLanguage.CHINESE -> "计量\n单位"
        AppLanguage.RUSSIAN -> "Единицы\nизмерения"
        else -> "Manage\nUnits"
    }

    fun actionCategories(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "कैटेगरी और\nहेडर"
        AppLanguage.SPANISH -> "Categorías\ny Pestañas"
        AppLanguage.CHINESE -> "商品分类\n与标签"
        AppLanguage.RUSSIAN -> "Категории\nи фильтры"
        else -> "Categories &\nHeaders"
    }

    fun actionReminder(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "व्हाट्सएप\nसंदेश"
        AppLanguage.SPANISH -> "Mensaje\nWhatsApp"
        AppLanguage.CHINESE -> "WhatsApp\n提醒模板"
        AppLanguage.RUSSIAN -> "WhatsApp\nшаблон"
        else -> "WhatsApp\nReminder"
    }

    // Modal Titles
    fun trashModalTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "हटाए गए बिल और रसीदें"
        AppLanguage.SPANISH -> "Recibos Eliminados"
        AppLanguage.CHINESE -> "已删除收据与账单"
        AppLanguage.RUSSIAN -> "Удаленные счета и чеки"
        else -> "Deleted Receipts Trash"
    }

    fun exportModalTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "ग्राहक बकाया राशि निर्यात करें"
        AppLanguage.SPANISH -> "Exportar Saldos de Clientes"
        AppLanguage.CHINESE -> "导出客户欠款结余"
        AppLanguage.RUSSIAN -> "Экспорт задолженностей клиентов"
        else -> "Export Customer Balances"
    }

    fun unitsModalTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "इकाइयाँ प्रबंधित करें"
        AppLanguage.SPANISH -> "Gestionar Unidades"
        AppLanguage.CHINESE -> "管理计量单位"
        AppLanguage.RUSSIAN -> "Управление единицами"
        else -> "Manage Units"
    }

    fun categoriesModalTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "श्रेणियां और टेबल हेडर"
        AppLanguage.SPANISH -> "Categorías y Encabezados"
        AppLanguage.CHINESE -> "分类与表头管理"
        AppLanguage.RUSSIAN -> "Категории и заголовки"
        else -> "Categories & Table Headers"
    }

    fun reminderModalTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "व्हाट्सएप रिमाइंडर टेम्पलेट"
        AppLanguage.SPANISH -> "Plantilla de Recordatorio"
        AppLanguage.CHINESE -> "WhatsApp 付款提醒模板"
        AppLanguage.RUSSIAN -> "Шаблон напоминания в WhatsApp"
        else -> "WhatsApp Reminder Template"
    }

    // Default WhatsApp reminder messages per language
    fun defaultReminderTemplate(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "नमस्ते {customer_name}, {business_name} की ओर से विनम्र अनुस्मारक कि {date} तक आपका बकाया ₹{amount} शेष है। कृपया यथाशीघ्र भुगतान करें। धन्यवाद!"
        AppLanguage.SPANISH -> "Hola {customer_name}, le recordamos cordialmente que tiene un saldo pendiente de {amount} con {business_name} al {date}. Agradecemos su pronto pago. ¡Muchas gracias!"
        AppLanguage.CHINESE -> "您好 {customer_name}，温馨提醒您在 {business_name} 截至 {date} 尚有未付结余 {amount}。请您方便时尽快结清款项。非常感谢！"
        AppLanguage.RUSSIAN -> "Здравствуйте, {customer_name}! Напоминаем, что по состоянию на {date} задолженность в {business_name} составляет {amount}. Пожалуйста, внесите оплату. Спасибо!"
        else -> "Hi {customer_name}, this is a gentle reminder that you have a pending balance of {amount} with {business_name} as of {date}. Please clear it at your earliest convenience. Thank you!"
    }

    // --- AUTH & LOGIN ---
    fun signInToLedger(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "खाता बुक में साइन इन करें"
        AppLanguage.SPANISH -> "Iniciar sesión en su Libro"
        AppLanguage.CHINESE -> "登录您的云端账本"
        AppLanguage.RUSSIAN -> "Войти в бухгалтерию"
        else -> "Sign in to your ledger"
    }

    fun loginSubtitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "ग्राहक, बिक्री और उधारी का सहज प्रबंधन करें"
        AppLanguage.SPANISH -> "Gestione clientes, ventas y deudas fácilmente"
        AppLanguage.CHINESE -> "轻松管理客户账目、销售与收据"
        AppLanguage.RUSSIAN -> "Управляйте клиентами, продажами и долгами"
        else -> "Manage customers, sales, and khata seamlessly"
    }

    fun notSignedIn(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "साइन इन नहीं हैं"
        AppLanguage.SPANISH -> "No conectado"
        AppLanguage.CHINESE -> "未登录"
        AppLanguage.RUSSIAN -> "Не авторизован"
        else -> "Not signed in"
    }

    fun checkingSession(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "सत्र जांचा जा रहा है..."
        AppLanguage.SPANISH -> "Comprobando sesión..."
        AppLanguage.CHINESE -> "检查会话中..."
        AppLanguage.RUSSIAN -> "Проверка сессии..."
        else -> "Checking session..."
    }

    fun email(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "ईमेल"
        AppLanguage.SPANISH -> "Correo electrónico"
        AppLanguage.CHINESE -> "电子邮箱"
        AppLanguage.RUSSIAN -> "Эл. почта"
        else -> "Email"
    }

    fun password(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "पासवर्ड"
        AppLanguage.SPANISH -> "Contraseña"
        AppLanguage.CHINESE -> "密码"
        AppLanguage.RUSSIAN -> "Пароль"
        else -> "Password"
    }

    fun signIn(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "लॉगिन करें"
        AppLanguage.SPANISH -> "Iniciar Sesión"
        AppLanguage.CHINESE -> "登录"
        AppLanguage.RUSSIAN -> "Войти"
        else -> "Sign In"
    }

    fun createAccount(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "नया खाता बनाएं"
        AppLanguage.SPANISH -> "Crear Cuenta"
        AppLanguage.CHINESE -> "注册账号"
        AppLanguage.RUSSIAN -> "Создать аккаунт"
        else -> "Create Account"
    }

    fun continueOffline(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "ऑफलाइन मोड में जारी रखें"
        AppLanguage.SPANISH -> "Continuar sin conexión"
        AppLanguage.CHINESE -> "离线继续使用"
        AppLanguage.RUSSIAN -> "Продолжить оффлайн"
        else -> "Continue with Local Storage"
    }

    // --- BUSINESS & RECEIPT DETAILS ---
    fun businessAndReceiptSettings(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "व्यापार एवं रसीद सेटिंग्स"
        AppLanguage.SPANISH -> "Ajustes de Negocio y Factura"
        AppLanguage.CHINESE -> "商户与收据配置"
        AppLanguage.RUSSIAN -> "Настройки бизнеса и чеков"
        else -> "Business & Receipt Settings"
    }

    fun businessName(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "व्यापार का नाम"
        AppLanguage.SPANISH -> "Nombre del Negocio"
        AppLanguage.CHINESE -> "商铺名称"
        AppLanguage.RUSSIAN -> "Название бизнеса"
        else -> "Business Name"
    }

    fun businessAddress(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "दुकान/व्यापार का पता"
        AppLanguage.SPANISH -> "Dirección del Negocio"
        AppLanguage.CHINESE -> "营业地址"
        AppLanguage.RUSSIAN -> "Адрес компании"
        else -> "Business Address"
    }

    fun contactNumber(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "सम्पर्क नंबर"
        AppLanguage.SPANISH -> "Teléfono de Contacto"
        AppLanguage.CHINESE -> "联系电话"
        AppLanguage.RUSSIAN -> "Контактный телефон"
        else -> "Contact Number"
    }

    fun upiId(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "UPI आईडी (QR कोड हेतु)"
        AppLanguage.SPANISH -> "ID de UPI (para código QR)"
        AppLanguage.CHINESE -> "UPI 支付账号 (QR 码)"
        AppLanguage.RUSSIAN -> "UPI ID (для QR-кода)"
        else -> "UPI ID (for QR Code)"
    }

    fun saveChanges(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "बदलाव सुरक्षित करें"
        AppLanguage.SPANISH -> "Guardar Cambios"
        AppLanguage.CHINESE -> "保存修改"
        AppLanguage.RUSSIAN -> "Сохранить изменения"
        else -> "Save Changes"
    }

    // --- SECURITY & PASSCODE ---
    fun requirePasscodeStartup(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "शुरुआत में पासकोड की आवश्यकता"
        AppLanguage.SPANISH -> "Requerir PIN al iniciar"
        AppLanguage.CHINESE -> "启动时要求锁屏密码"
        AppLanguage.RUSSIAN -> "Запрашивать PIN при входе"
        else -> "Require Passcode on Startup"
    }

    fun pinLockActive(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "पिन लॉक सक्रिय है"
        AppLanguage.SPANISH -> "Bloqueo por PIN activo"
        AppLanguage.CHINESE -> "PIN 锁已启用"
        AppLanguage.RUSSIAN -> "PIN-код активен"
        else -> "PIN lock active"
    }

    fun requirePinToOpen(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "ऐप खोलने के लिए 4 अंकों का पिन आवश्यक"
        AppLanguage.SPANISH -> "Requiere PIN de 4 dígitos para abrir"
        AppLanguage.CHINESE -> "需输入 4 位数字 PIN 解锁"
        AppLanguage.RUSSIAN -> "Требуется 4-значный PIN для входа"
        else -> "Require 4-digit PIN to open LedgerMate"
    }

    fun changePin(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "4 अंकों का पिन बदलें:"
        AppLanguage.SPANISH -> "Cambiar PIN de 4 dígitos:"
        AppLanguage.CHINESE -> "更改 4 位 PIN 码:"
        AppLanguage.RUSSIAN -> "Изменить 4-значный PIN:"
        else -> "Change 4-Digit PIN:"
    }

    fun enterPin(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "4 अंकों का पिन दर्ज करें:"
        AppLanguage.SPANISH -> "Introduzca PIN de 4 dígitos:"
        AppLanguage.CHINESE -> "输入 4 位 PIN 码:"
        AppLanguage.RUSSIAN -> "Введите 4-значный PIN:"
        else -> "Enter 4-Digit PIN:"
    }

    fun biometricUnlock(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "बायोमेट्रिक अनलॉक"
        AppLanguage.SPANISH -> "Desbloqueo Biométrico"
        AppLanguage.CHINESE -> "生物识别解锁"
        AppLanguage.RUSSIAN -> "Биометрическая разблокировка"
        else -> "Biometric Unlock"
    }

    // --- MODAL DIALOG BODIES ---
    fun autoSyncFrequencyDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "चुनें कि लेजरमेट स्थानीय रिकॉर्ड को क्लाउड से कितनी बार सिंक करे।"
        AppLanguage.SPANISH -> "Elija con qué frecuencia sincronizar los registros con la nube."
        AppLanguage.CHINESE -> "选择云端备份的自动同步频率。"
        AppLanguage.RUSSIAN -> "Выберите частоту синхронизации записей с облаком."
        else -> "Choose how frequently LedgerMate synchronizes local records with cloud backup."
    }

    fun exportLedgerDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "ग्राहक बकाया राशि (नाम, फोन, कुल बकाया) को CSV में निर्यात करें।"
        AppLanguage.SPANISH -> "Exportar saldos pendientes de clientes a archivo CSV estándar."
        AppLanguage.CHINESE -> "导出包含客户姓名、电话及总欠款的标准化 CSV 报表。"
        AppLanguage.RUSSIAN -> "Экспорт задолженностей (имя, телефон, баланс) в CSV файл."
        else -> "Export customer pending balances (Customer Name, Phone, Total Pending Amount) to a standardized CSV."
    }

    fun totalCustomers(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "कुल ग्राहक:"
        AppLanguage.SPANISH -> "Total Clientes:"
        AppLanguage.CHINESE -> "客户总数:"
        AppLanguage.RUSSIAN -> "Всего клиентов:"
        else -> "Total Customers:"
    }

    fun customersWithDues(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "उधारी वाले ग्राहक:"
        AppLanguage.SPANISH -> "Clientes con Deuda:"
        AppLanguage.CHINESE -> "有未结清账款客户:"
        AppLanguage.RUSSIAN -> "Клиенты с долгами:"
        else -> "Customers with Dues:"
    }

    fun totalOutstanding(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "कुल बकाया राशि:"
        AppLanguage.SPANISH -> "Saldo Total Pendiente:"
        AppLanguage.CHINESE -> "总待结欠款:"
        AppLanguage.RUSSIAN -> "Общий долг:"
        else -> "Total Outstanding:"
    }

    fun exportAndShareCsv(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "CSV निर्यात और साझा करें"
        AppLanguage.SPANISH -> "Exportar y Compartir CSV"
        AppLanguage.CHINESE -> "导出并分享 CSV"
        AppLanguage.RUSSIAN -> "Экспорт и отправка CSV"
        else -> "Export & Share CSV"
    }

    fun noDeletedReceipts(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "कोई हटाई गई रसीद नहीं है"
        AppLanguage.SPANISH -> "No hay recibos eliminados"
        AppLanguage.CHINESE -> "回收站中暂无已删除收据"
        AppLanguage.RUSSIAN -> "Нет удаленных чеков"
        else -> "No deleted receipts"
    }

    fun unitsDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "हैंडल (≡) को खींचकर इकाइयों का क्रम बदलें।"
        AppLanguage.SPANISH -> "Arrastre el icono (≡) para reordenar las unidades."
        AppLanguage.CHINESE -> "按住拖动图标 (≡) 调整单位排序。"
        AppLanguage.RUSSIAN -> "Перетаскивайте (≡) для изменения порядка единиц."
        else -> "Drag handle (≡) to reorder units across rate items and bills."
    }

    fun categoriesDesc(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "आइकन पर टैप कर प्रतीक बदलें, (≡) से क्रम बदलें, या नाम संपादित करें।"
        AppLanguage.SPANISH -> "Toque el icono para cambiarlo, arrastre (≡) para reordenar o edite el nombre."
        AppLanguage.CHINESE -> "轻触图标修改矢量图，拖动 (≡) 排序，或重命名分类。"
        AppLanguage.RUSSIAN -> "Нажмите на иконку для смены, перетащите (≡) для сортировки."
        else -> "Tap icon to change vector, drag (≡) to reorder, or edit names."
    }
}
