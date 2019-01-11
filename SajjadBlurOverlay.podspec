Pod::Spec.new do |s|
  s.name         = "SajjadBlurOverlay"
  s.version      = "1.0.0"
  s.summary      = "SajjadBlurOverlay"
  s.description  = <<-DESC
                  SajjadBlurOverlay
                   DESC
  s.homepage     = "https://github.com/lvlrSajjad/react-native-blur-overlay"
  s.license      = "MIT"
  # s.license      = { :type => "MIT", :file => "FILE_LICENSE" }
  s.author             = { "author" => "author@domain.cn" }
  s.platform     = :ios, "7.0"
  s.source       = { :git => "https://github.com/author/SajjadBlurOverlay.git", :tag => "master" }
  s.source_files  = "SajjadBlurOverlay/**/*.{h,m}"
  s.requires_arc = true


  s.dependency "React"
  #s.dependency "others"

end
