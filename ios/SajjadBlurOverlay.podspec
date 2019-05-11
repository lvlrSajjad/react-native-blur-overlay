require 'json'

package = JSON.parse(File.read(File.join(__dir__, '../package.json')))

Pod::Spec.new do |s|
  s.name         = "SajjadBlurOverlay"
  s.version      = package['version']
  s.summary      = "SajjadBlurOverlay"
  s.description  = package['description']
  s.homepage     = package['homepage']
  s.license      = package['license']
  s.author       = { "author" => "author@domain.cn" }
  s.platform     = :ios, "7.0"
  s.source       = { :git => "https://github.com/author/SajjadBlurOverlay.git", :tag => "master" }
  s.source_files  = "SajjadBlurOverlay/**/*.{h,m}"
  s.requires_arc = true


  s.dependency "React"
  #s.dependency "others"

end
  
