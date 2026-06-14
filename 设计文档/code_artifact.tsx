import React, { useState } from 'react';
import { Wifi, Signal, Battery, ChevronDown, ChevronRight } from 'lucide-react';

export default function App() {
  const [isPremium, setIsPremium] = useState(true);

  // 美化版黄金图标
  const PremiumGoldIcon = () => (
    <div className="w-5 h-5 rounded-md bg-gradient-to-br from-yellow-200 via-yellow-400 to-yellow-600 flex items-center justify-center shadow-sm border border-yellow-300/50">
      <span className="text-[#5c4000] text-[9px] font-black tracking-tighter">Au</span>
    </div>
  );

  // 原始安卓默认图标
  const DefaultAndroidIcon = () => (
    <div className="w-5 h-5 rounded-md bg-[#3DDB85] flex items-center justify-center relative overflow-hidden grid-bg">
      {/* 简单的网格背景模拟 */}
      <div className="absolute inset-0 opacity-20" style={{ backgroundImage: 'linear-gradient(#fff 1px, transparent 1px), linear-gradient(90deg, #fff 1px, transparent 1px)', backgroundSize: '4px 4px' }}></div>
      <svg width="12" height="12" viewBox="0 0 24 24" fill="white" className="z-10">
        <path d="M17.5 7.63l1.85-1.85c.18-.18.18-.48 0-.66a.47.47 0 00-.66 0L16.8 7a9.92 9.92 0 00-9.6 0L5.3 5.12a.47.47 0 00-.66 0c-.18.18-.18.48 0 .66L6.5 7.63A10.84 10.84 0 002 17h20a10.84 10.84 0 00-4.5-9.37zM8.5 14a1.5 1.5 0 110-3 1.5 1.5 0 010 3zm7 0a1.5 1.5 0 110-3 1.5 1.5 0 010 3z" />
      </svg>
    </div>
  );

  return (
    <div className="min-h-screen bg-neutral-900 flex items-center justify-center p-4 font-sans">
      
      {/* 手机壳模拟 */}
      <div className="relative w-[380px] h-[800px] bg-black rounded-[3rem] p-3 shadow-2xl overflow-hidden border-4 border-neutral-800">
        
        {/* 屏幕壁纸 (使用CSS渐变模拟你的截图背景) */}
        <div className="absolute inset-0 bg-gradient-to-b from-[#8ab4c9] via-[#b3c7cd] to-[#dcaea9] rounded-[2.5rem] overflow-hidden">
          
          {/* 顶部状态栏 */}
          <div className="flex justify-between items-center px-6 pt-4 pb-2 text-white/90 text-sm font-medium">
            <span>16:19</span>
            <div className="flex items-center gap-1.5">
              <span className="text-xs mr-1 bg-green-500/80 px-1 rounded-sm">63</span>
              <Signal size={14} className="fill-current" />
              <Wifi size={14} />
              <Battery size={16} className="fill-current" />
            </div>
          </div>

          {/* 控制中心小组件占位 (让场景更逼真) */}
          <div className="px-4 mt-4 opacity-50 flex gap-4">
             <div className="flex-1 h-24 bg-white/20 rounded-2xl backdrop-blur-md"></div>
             <div className="flex-1 h-24 bg-white/20 rounded-2xl backdrop-blur-md"></div>
          </div>

          {/* 通知区域 */}
          <div className="px-4 mt-6">
            
            {/* 切换开关 */}
            <div className="flex justify-center mb-6">
              <div className="bg-black/20 backdrop-blur-md p-1 rounded-full flex gap-1 shadow-inner">
                <button 
                  onClick={() => setIsPremium(false)}
                  className={`px-4 py-1.5 rounded-full text-sm font-medium transition-all ${!isPremium ? 'bg-white text-black shadow-sm' : 'text-white/70 hover:text-white'}`}
                >
                  原版样式
                </button>
                <button 
                  onClick={() => setIsPremium(true)}
                  className={`px-4 py-1.5 rounded-full text-sm font-medium transition-all ${isPremium ? 'bg-white text-black shadow-sm' : 'text-white/70 hover:text-white'}`}
                >
                  美化版样式
                </button>
              </div>
            </div>

            {/* 通知卡片 - 根据状态切换 */}
            <div className="transition-all duration-500 ease-in-out">
              
              {!isPremium ? (
                /* --- 原始样式复刻 --- */
                <div className="bg-[#f0ece9]/90 backdrop-blur-xl rounded-3xl p-4 shadow-sm">
                  {/* Header */}
                  <div className="flex justify-between items-center mb-2">
                    <div className="flex items-center gap-2">
                      <DefaultAndroidIcon />
                      <span className="text-[13px] text-gray-800">黄金价格</span>
                    </div>
                    <div className="flex items-center gap-1 text-gray-500">
                      <span className="text-[12px]">刚刚</span>
                      <ChevronDown size={14} />
                    </div>
                  </div>
                  
                  {/* Body (纯文本堆砌) */}
                  <div className="text-gray-800 leading-[1.6]">
                    <div className="text-[15px] font-medium mb-1">黄金 896.01 元/克</div>
                    <div className="text-[14px]">黄金9999 XAU 896.01 元/克</div>
                    <div className="text-[14px]">-19.62(-2.14%) 2026-06-11 15:38:25</div>
                    <div className="text-[14px]">今开 915.00 昨收 915.63</div>
                    <div className="text-[14px]">最高 915.00 最低 883.81</div>
                    <div className="text-[14px] text-gray-500 mt-0.5">行情数据可能延迟</div>
                  </div>
                </div>
              ) : (
                /* --- 美化版样式 --- */
                <div className="bg-white/75 backdrop-blur-2xl rounded-[28px] p-4 shadow-[0_8px_30px_rgb(0,0,0,0.08)] border border-white/40 relative overflow-hidden group">
                  
                  {/* 一点光泽效果 */}
                  <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-white/80 to-transparent"></div>

                  {/* Header */}
                  <div className="flex justify-between items-center mb-3">
                    <div className="flex items-center gap-2">
                      <PremiumGoldIcon />
                      <span className="text-[13px] font-medium text-gray-700 tracking-wide">黄金价格</span>
                    </div>
                    <div className="flex items-center gap-1 text-gray-400">
                      <span className="text-[11px] font-medium">刚刚</span>
                      <ChevronDown size={14} className="opacity-70 group-hover:opacity-100 transition-opacity" />
                    </div>
                  </div>
                  
                  {/* Main Content */}
                  <div className="px-1">
                    {/* 标题 & 标签 */}
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-sm font-semibold text-gray-800">黄金9999 (XAU)</span>
                    </div>

                    {/* 核心价格区 */}
                    <div className="flex items-baseline gap-2 mb-4">
                      <span className="text-[34px] font-bold text-gray-900 tracking-tight leading-none">896.01</span>
                      <span className="text-[13px] text-gray-500 font-medium">元/克</span>
                      
                      {/* 涨跌幅 - 采用国内绿跌红涨标准 */}
                      <div className="ml-auto flex items-center gap-1 bg-[#eafff0] border border-[#a8f0c2] text-[#0d9b4b] px-2 py-1 rounded-lg">
                        <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" className="rotate-45">
                          <path d="M17 7l-10 10" />
                          <path d="M17 17V7H7" />
                        </svg>
                        <span className="text-[13px] font-bold tracking-tight">-19.62 (-2.14%)</span>
                      </div>
                    </div>

                    {/* 数据网格卡片 */}
                    <div className="bg-white/50 rounded-xl p-2.5 mb-3 grid grid-cols-4 gap-2 border border-white/40">
                      <div className="flex flex-col items-center">
                        <span className="text-[10px] text-gray-400 mb-0.5">今开</span>
                        <span className="text-[13px] font-semibold text-gray-700">915.00</span>
                      </div>
                      <div className="flex flex-col items-center border-l border-gray-200/50">
                        <span className="text-[10px] text-gray-400 mb-0.5">昨收</span>
                        <span className="text-[13px] font-semibold text-gray-700">915.63</span>
                      </div>
                      <div className="flex flex-col items-center border-l border-gray-200/50">
                        <span className="text-[10px] text-gray-400 mb-0.5">最高</span>
                        <span className="text-[13px] font-semibold text-gray-700">915.00</span>
                      </div>
                      <div className="flex flex-col items-center border-l border-gray-200/50">
                        <span className="text-[10px] text-gray-400 mb-0.5">最低</span>
                        <span className="text-[13px] font-semibold text-green-600">883.81</span>
                      </div>
                    </div>

                    {/* Footer 信息 */}
                    <div className="flex justify-between items-center mt-1">
                      <span className="text-[11px] text-gray-400 font-medium">2026-06-11 15:38:25</span>
                      <span className="text-[10px] text-gray-400/80">行情数据可能延迟</span>
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* 底部其他通知占位 */}
            <div className="mt-2 bg-[#f0ece9]/60 backdrop-blur-xl rounded-3xl p-4 shadow-sm opacity-80 h-16 flex items-center">
               <div className="w-6 h-6 rounded bg-gray-400/30 mr-3"></div>
               <div className="h-3 w-32 bg-gray-400/30 rounded"></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}