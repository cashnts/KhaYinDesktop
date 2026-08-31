#!/usr/bin/env python3
import os
import shutil
import subprocess
import sys
import tempfile
import time

def run_cmd(cmd, check=True):
    print(f"==> {' '.join(cmd)}")
    return subprocess.run(cmd, check=check, capture_output=True, text=True)

def create_styled_dmg(app_bundle_path, output_dmg_path, app_name="KhaYin", volume_icon=None):
    if not os.path.exists(app_bundle_path):
        raise FileNotFoundError(f"App bundle not found: {app_bundle_path}")

    output_dir = os.path.dirname(os.path.abspath(output_dmg_path))
    os.makedirs(output_dir, exist_ok=True)

    temp_dir = tempfile.mkdtemp(prefix="khayin_dmg_build_")
    staging_dir = os.path.join(temp_dir, "staging")
    os.makedirs(staging_dir, exist_ok=True)
    mount_dir = os.path.join(temp_dir, "mount")
    os.makedirs(mount_dir, exist_ok=True)
    rw_dmg = os.path.join(temp_dir, "temp_rw.dmg")

    app_symlink = os.path.join(staging_dir, "Applications")
    mounted = False

    try:
        print(f"Staging app bundle: {app_bundle_path} -> {staging_dir}")
        target_app = os.path.join(staging_dir, f"{app_name}.app")
        run_cmd(["ditto", app_bundle_path, target_app])

        # Create /Applications drag & drop symlink
        os.symlink("/Applications", app_symlink)

        # Generate custom Retina background
        bg_dir = os.path.join(staging_dir, ".background")
        os.makedirs(bg_dir, exist_ok=True)
        script_dir = os.path.dirname(os.path.abspath(__file__))
        gen_bg_script = os.path.join(script_dir, "generate_dmg_background.py")
        if os.path.exists(gen_bg_script):
            run_cmd([sys.executable, gen_bg_script, app_name, bg_dir])

        # Copy Volume Icon
        if volume_icon and os.path.exists(volume_icon):
            vol_icon_target = os.path.join(staging_dir, ".VolumeIcon.icns")
            shutil.copyfile(volume_icon, vol_icon_target)

        # Set hidden attributes
        run_cmd(["/usr/bin/SetFile", "-a", "V", bg_dir], check=False)
        if os.path.exists(os.path.join(staging_dir, ".VolumeIcon.icns")):
            run_cmd(["/usr/bin/SetFile", "-a", "V", os.path.join(staging_dir, ".VolumeIcon.icns")], check=False)

        # Create RW DMG from staging directory
        print("Creating writable DMG for Finder layout styling...")
        run_cmd([
            "hdiutil", "create",
            "-srcfolder", staging_dir,
            "-volname", app_name,
            "-fs", "HFS+",
            "-fsargs", "-c c=64,a=16,e=16",
            "-format", "UDRW",
            "-size", "600m",
            rw_dmg,
            "-ov"
        ])

        # Mount RW DMG
        print("Mounting DMG to apply AppleScript Finder view configuration...")
        attach_res = run_cmd([
            "hdiutil", "attach",
            rw_dmg,
            "-noautoopen",
            "-nobrowse",
            "-mountpoint", mount_dir
        ])
        mounted = True

        # Apply Volume Icon attribute on mountpoint
        run_cmd(["/usr/bin/SetFile", "-a", "C", mount_dir], check=False)

        # Apply AppleScript styling to Finder window
        bg_file = "background.tiff" if os.path.exists(os.path.join(bg_dir, "background.tiff")) else "background.png"
        applescript = f"""
        tell application "Finder"
            tell disk (POSIX file "{mount_dir}" as alias)
                open
                set current view of container window to icon view
                set toolbar visible of container window to false
                set statusbar visible of container window to false
                set the bounds of container window to {{300, 150, 960, 580}}
                set theViewOptions to the icon view options of container window
                set arrangement of theViewOptions to not arranged
                set icon size of theViewOptions to 112
                set text size of theViewOptions to 13
                set label position of theViewOptions to bottom
                try
                    set background picture of theViewOptions to file ".background:{bg_file}"
                end try
                set position of item "{app_name}.app" of container window to {{175, 185}}
                set position of item "Applications" of container window to {{485, 185}}
                update without registering applications
                delay 1
                close
            end tell
        end tell
        """

        try:
            print("Applying Finder visual layout via osascript...")
            run_cmd(["osascript", "-e", applescript])
        except Exception as e:
            print(f"Warning: AppleScript Finder styling encountered non-fatal notice: {e}")

        # Ensure changes are flushed
        run_cmd(["sync"], check=False)
        time.sleep(1)

        # Detach DMG
        print("Detaching DMG...")
        run_cmd(["hdiutil", "detach", mount_dir, "-force"])
        mounted = False

        # Convert to final compressed UDZO DMG
        if os.path.exists(output_dmg_path):
            os.remove(output_dmg_path)

        print(f"Converting to compressed read-only DMG -> {output_dmg_path}")
        run_cmd([
            "hdiutil", "convert",
            rw_dmg,
            "-format", "UDZO",
            "-imagekey", "zlib-level=9",
            "-o", output_dmg_path,
            "-ov"
        ])
        print(f"Successfully generated styled DMG: {output_dmg_path}")

    finally:
        if mounted:
            try:
                run_cmd(["hdiutil", "detach", mount_dir, "-force"], check=False)
            except Exception:
                pass

        # Strictly unlink /Applications first
        try:
            if os.path.islink(app_symlink) or os.path.exists(app_symlink):
                os.unlink(app_symlink)
        except Exception:
            pass

        # Clean up temp files safely
        try:
            shutil.rmtree(temp_dir, ignore_errors=True)
        except Exception:
            pass

def main():
    if len(sys.argv) < 3:
        print("Usage: create_styled_dmg.py <app_bundle_path> <output_dmg_path> [app_name] [volume_icon]")
        sys.exit(1)

    app_bundle = sys.argv[1]
    output_dmg = sys.argv[2]
    app_name = sys.argv[3] if len(sys.argv) > 3 else "KhaYin"
    vol_icon = sys.argv[4] if len(sys.argv) > 4 else None

    create_styled_dmg(app_bundle, output_dmg, app_name=app_name, volume_icon=vol_icon)

if __name__ == "__main__":
    main()
