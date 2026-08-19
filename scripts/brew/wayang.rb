class Wayang < Formula
  desc "Autonomous Coding Agent Substrate & Gollek Neural Engine"
  homepage "https://github.com/kayys-tech/wayang-platform"
  version "0.1.0"

  on_macos do
    if Hardware::CPU.arm?
      url "https://github.com/kayys-tech/wayang-platform/releases/download/v0.1.0/wayang-macos-arm64.tar.gz"
      sha256 "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    else
      url "https://github.com/kayys-tech/wayang-platform/releases/download/v0.1.0/wayang-macos-x86_64.tar.gz"
      sha256 "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    end
  end

  depends_on "openjdk@21"

  def install
    bin.install "bin/wayang"
    bin.install "bin/aljabr"
    bin.install "bin/gollek"
    prefix.install Dir["lib/*"]
  end

  def caveats
    <<~EOS
      🎉 Wayang Platform & Gollek Inference Engine installed!
      Run `wayang` or launch Aljabr Studio GUI to start the dual-substrate environment.
    EOS
  end

  test do
    system "#{bin}/wayang", "--version"
  end
end
