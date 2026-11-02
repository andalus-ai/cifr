//! Shell integration (Settings › Tools › Terminal › Shell integration): the
//! shell reports its working directory (OSC 7) at every prompt, so links and
//! new splits resolve against the directory the user `cd`-ed into, and marks
//! where a command starts and ends (OSC 133 C / D), so the tab shows the
//! running program's title only while one runs.
//!
//! zsh starts with `ZDOTDIR` pointing at generated startup files that source
//! the user's own ones first; bash gets an `--init-file` that replays the
//! login sequence; fish gets an `--init-command`.

use std::collections::BTreeMap;
use std::path::{Path, PathBuf};

const ZSHENV: &str = r#"# CifrIDE shell integration (generated)
_cifridea_dir=$ZDOTDIR
ZDOTDIR=${CIFR_USER_ZDOTDIR:-$HOME}
[[ -f $ZDOTDIR/.zshenv ]] && . $ZDOTDIR/.zshenv
CIFR_USER_ZDOTDIR=$ZDOTDIR
ZDOTDIR=$_cifridea_dir
"#;

const ZPROFILE: &str = r#"# CifrIDE shell integration (generated)
ZDOTDIR=$CIFR_USER_ZDOTDIR
[[ -f $ZDOTDIR/.zprofile ]] && . $ZDOTDIR/.zprofile
CIFR_USER_ZDOTDIR=$ZDOTDIR
ZDOTDIR=$_cifridea_dir
"#;

const ZSHRC: &str = r#"# CifrIDE shell integration (generated)
ZDOTDIR=$CIFR_USER_ZDOTDIR
[[ -f $ZDOTDIR/.zshrc ]] && . $ZDOTDIR/.zshrc
CIFR_USER_ZDOTDIR=$ZDOTDIR
ZDOTDIR=$_cifridea_dir
_cifridea_osc7() { printf '\e]133;D\a\e]7;file://%s%s\a' "${HOST}" "${PWD}" }
_cifridea_preexec() { printf '\e]133;C\a' }
# First, so the command counts as finished before other hooks set the prompt's title.
precmd_functions=(_cifridea_osc7 ${precmd_functions:#_cifridea_osc7})
preexec_functions+=(_cifridea_preexec)
if [[ ! -o login ]]; then
  ZDOTDIR=$CIFR_USER_ZDOTDIR
  unset CIFR_USER_ZDOTDIR _cifridea_dir
fi
"#;

const ZLOGIN: &str = r#"# CifrIDE shell integration (generated)
ZDOTDIR=$CIFR_USER_ZDOTDIR
[[ -f $ZDOTDIR/.zlogin ]] && . $ZDOTDIR/.zlogin
unset CIFR_USER_ZDOTDIR _cifridea_dir
"#;

const BASHRC: &str = r#"# CifrIDE shell integration (generated)
if [ "${CIFR_LOGIN:-}" = 1 ]; then
  unset CIFR_LOGIN
  [ -f /etc/profile ] && . /etc/profile
  if [ -f ~/.bash_profile ]; then . ~/.bash_profile
  elif [ -f ~/.bash_login ]; then . ~/.bash_login
  elif [ -f ~/.profile ]; then . ~/.profile
  fi
else
  [ -f ~/.bashrc ] && . ~/.bashrc
fi
__cifridea_osc7() { printf '\e]133;D\a\e]7;file://%s%s\a' "${HOSTNAME}" "${PWD}"; }
case ";${PROMPT_COMMAND:-};" in
  *";__cifridea_osc7;"*) ;;
  *) PROMPT_COMMAND="__cifridea_osc7${PROMPT_COMMAND:+;$PROMPT_COMMAND}" ;;
esac
# PS0 (bash 4.4+) prints before each command runs.
case "${PS0:-}" in
  *'133;C'*) ;;
  *) PS0="${PS0:-}\e]133;C\a" ;;
esac
"#;

const FISH_INIT: &str = concat!(
    r#"function __cifridea_osc7 --on-event fish_prompt; printf '\e]133;D\a\e]7;file://%s%s\a' (hostname) $PWD; end; "#,
    r#"function __cifridea_preexec --on-event fish_preexec; printf '\e]133;C\a'; end"#,
);

/// Directory holding the generated startup files (written on demand).
fn scripts_dir() -> PathBuf {
    std::env::temp_dir().join(format!("cifridea-shell-integration-{}", env!("CARGO_PKG_VERSION")))
}

fn write_if_changed(path: &Path, content: &str) -> std::io::Result<()> {
    if std::fs::read_to_string(path).is_ok_and(|c| c == content) {
        return Ok(());
    }
    if let Some(dir) = path.parent() {
        std::fs::create_dir_all(dir)?;
    }
    std::fs::write(path, content)
}

/// Adjusts how `shell` is started so it reports its cwd. `args` are the
/// resolved arguments; returns `false` (leaving everything untouched) for
/// shells without integration or when the scripts cannot be written.
pub(crate) fn apply(shell_name: &str, args: &mut Vec<String>, env: &mut BTreeMap<String, String>) -> bool {
    if !cfg!(unix) {
        return false;
    }
    let dir = scripts_dir();
    let result = match shell_name {
        "zsh" => (|| -> std::io::Result<()> {
            let zdir = dir.join("zsh");
            write_if_changed(&zdir.join(".zshenv"), ZSHENV)?;
            write_if_changed(&zdir.join(".zprofile"), ZPROFILE)?;
            write_if_changed(&zdir.join(".zshrc"), ZSHRC)?;
            write_if_changed(&zdir.join(".zlogin"), ZLOGIN)?;
            let user = env.get("ZDOTDIR").cloned().or_else(|| std::env::var("ZDOTDIR").ok()).filter(|v| !v.is_empty());
            if let Some(user) = user {
                env.insert("CIFR_USER_ZDOTDIR".into(), user);
            }
            env.insert("ZDOTDIR".into(), zdir.to_string_lossy().into_owned());
            Ok(())
        })(),
        "bash" => {
            // Only plain interactive starts (`bash` / `bash -l`); explicit scripts keep their arguments.
            if args.iter().any(|a| a != "-l" && a != "--login" && a != "-i") {
                return false;
            }
            (|| -> std::io::Result<()> {
                let rc = dir.join("bash").join("cifridea.bashrc");
                write_if_changed(&rc, BASHRC)?;
                if args.iter().any(|a| a == "-l" || a == "--login") {
                    env.insert("CIFR_LOGIN".into(), "1".into());
                }
                *args = vec!["--init-file".into(), rc.to_string_lossy().into_owned(), "-i".into()];
                Ok(())
            })()
        }
        "fish" => {
            args.push("--init-command".into());
            args.push(FISH_INIT.into());
            Ok(())
        }
        _ => return false,
    };
    match result {
        Ok(()) => true,
        Err(e) => {
            tracing::warn!("shell integration unavailable: {e}");
            false
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bash_gets_init_file_and_login_flag() {
        if cfg!(windows) {
            return; // Unix paths and shells
        }
        let mut args = vec!["-l".to_owned()];
        let mut env = BTreeMap::new();
        assert!(apply("bash", &mut args, &mut env));
        assert_eq!(args[0], "--init-file");
        assert!(Path::new(&args[1]).is_file());
        assert_eq!(env.get("CIFR_LOGIN").map(String::as_str), Some("1"));
    }

    #[test]
    fn scripts_are_left_alone() {
        let mut args = vec!["-c".to_owned(), "echo hi".to_owned()];
        let mut env = BTreeMap::new();
        assert!(!apply("bash", &mut args, &mut env));
        assert_eq!(args, vec!["-c", "echo hi"]);
        assert!(!apply("tcsh", &mut args, &mut env));
    }

    #[test]
    fn zsh_points_zdotdir_at_generated_files() {
        if cfg!(windows) {
            return; // Unix paths and shells
        }
        let mut args = vec!["-l".to_owned()];
        let mut env = BTreeMap::from([("ZDOTDIR".to_owned(), "/home/me/zsh".to_owned())]);
        assert!(apply("zsh", &mut args, &mut env));
        let dir = PathBuf::from(&env["ZDOTDIR"]);
        assert!(dir.join(".zshrc").is_file());
        assert_eq!(env["CIFR_USER_ZDOTDIR"], "/home/me/zsh");
        assert_eq!(args, vec!["-l"]);
    }
}
