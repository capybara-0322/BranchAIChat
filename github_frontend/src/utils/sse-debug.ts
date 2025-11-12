// SSE调试工具
export function debugSSEFormat(rawData: string) {
  console.group('🔍 SSE Format Debug')
  console.log('Raw data:', JSON.stringify(rawData))
  
  // 按双换行符分割事件
  const events = rawData.split('\n\n').filter(e => e.trim())
  console.log(`Found ${events.length} events`)
  
  events.forEach((event, index) => {
    console.group(`Event ${index + 1}`)
    console.log('Raw event:', JSON.stringify(event))
    
    const lines = event.split('\n')
    let eventName = 'message'
    const dataLines: string[] = []
    
    lines.forEach(line => {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
        console.log('Event name:', eventName)
      } else if (line.startsWith('data:')) {
        const data = line.slice(5)
        dataLines.push(data)
        console.log('Data line:', JSON.stringify(data))
      } else if (line.trim()) {
        console.log('Other line:', JSON.stringify(line))
      }
    })
    
    const combinedData = dataLines.join('\n').trim()
    console.log('Combined data:', JSON.stringify(combinedData))
    
    // 尝试解析JSON
    try {
      const parsed = JSON.parse(combinedData)
      console.log('Parsed JSON:', parsed)
      
      // 尝试提取文本内容
      const textFields = ['text', 'content', 'delta', 'message']
      for (const field of textFields) {
        if (parsed[field]) {
          console.log(`Found text in field "${field}":`, JSON.stringify(parsed[field]))
        }
      }
    } catch (e) {
      console.log('JSON parse failed:', e.message)
      console.log('Treating as plain text:', JSON.stringify(combinedData))
    }
    
    console.groupEnd()
  })
  
  console.groupEnd()
}

// 测试常见的SSE格式
export function testSSEFormats() {
  console.group('🧪 Testing Common SSE Formats')
  
  // 格式1: 标准SSE
  const format1 = `event: delta
data: {"text": "Hello"}

event: delta  
data: {"text": " World"}

event: done
data: {}`
  
  console.log('Testing Format 1 (Standard SSE):')
  debugSSEFormat(format1)
  
  // 格式2: 嵌套SSE
  const format2 = `data: event: chunk
data: data: {"text": "Hello"}

data: event: chunk
data: data: {"text": " World"}

data: event: done
data: data: {}`
  
  console.log('Testing Format 2 (Nested SSE):')
  debugSSEFormat(format2)
  
  // 格式3: 你的实际格式
  const format3 = `data:event: chunk
data:data: {"finish_reason":null,"delta":"你好","index":0,"sid":"503b0e12-7478-4a18-ae8c-4ef020fe75b2","gen_id":"g-35cdb1b4-ae5f-4b99-8ba9-935d66d09560"}
data:
data:

data:event: chunk
data:data: {"finish_reason":null,"delta":"！","index":1,"sid":"503b0e12-7478-4a18-ae8c-4ef020fe75b2","gen_id":"g-35cdb1b4-ae5f-4b99-8ba9-935d66d09560"}
data:
data:`
  
  console.log('Testing Format 3 (Your Actual Format):')
  debugSSEFormat(format3)
  
  // 格式4: 简单文本
  const format4 = `data: Hello
data:  World
data: !`
  
  console.log('Testing Format 4 (Simple Text):')
  debugSSEFormat(format4)
  
  console.groupEnd()
}
