fn main() {
    if std::env::var("TARGET").is_ok_and(|target| target.contains("android")) {
        let manifest = std::path::Path::new(&std::env::var("CARGO_MANIFEST_DIR").unwrap())
            .join("gen/android/app/src/main/AndroidManifest.xml");
        println!("cargo:rerun-if-changed={}", manifest.display());
        let contents = std::fs::read_to_string(&manifest)
            .unwrap_or_else(|_| panic!("Android manifest missing at {}", manifest.display()));
        assert!(
            contents.contains("android:allowBackup=\"false\"")
                && contents.contains("android:fullBackupContent=\"false\""),
            "Android backup exclusions must be applied with `npm run android:secure` before every Android build"
        );
    }
    tauri_build::build()
}
