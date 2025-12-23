import React, { useState } from 'react';
import Editor from '@monaco-editor/react';
import type { components } from '../../../../oieapi-types/index.d.ts';

type Channel = components['schemas']['Channel'];

interface ChannelScriptsEditorProps {
  channel: Channel;
  onChange: (channel: Channel) => void;
}

type ScriptType =
  | 'deployScript'
  | 'undeployScript'
  | 'preprocessingScript'
  | 'postprocessingScript';

const ChannelScriptsEditor: React.FC<ChannelScriptsEditorProps> = ({
  channel,
  onChange,
}) => {
  const [activeTab, setActiveTab] = useState<ScriptType>('deployScript');

  const handleEditorChange = (value: string | undefined) => {
    onChange({
      ...channel,
      [activeTab]: value || '',
    });
  };

  const tabs: { id: ScriptType; label: string }[] = [
    { id: 'deployScript', label: 'Deploy' },
    { id: 'undeployScript', label: 'Undeploy' },
    { id: 'preprocessingScript', label: 'Preprocessing' },
    { id: 'postprocessingScript', label: 'Postprocessing' },
  ];

  return (
    <div
      className='container mt-3 d-flex flex-column'
      style={{ height: '500px' }}
    >
      <ul className='nav nav-tabs mb-3'>
        {tabs.map((tab) => (
          <li className='nav-item' key={tab.id}>
            <button
              className={`nav-link ${activeTab === tab.id ? 'active' : ''}`}
              onClick={() => setActiveTab(tab.id)}
            >
              {tab.label}
            </button>
          </li>
        ))}
      </ul>
      <div className='flex-grow-1 border'>
        <Editor
          height='100%'
          language='javascript'
          theme='vs-dark'
          value={channel[activeTab] || ''}
          onChange={handleEditorChange}
          options={{
            minimap: { enabled: false },
            fontSize: 14,
            scrollBeyondLastLine: false,
            automaticLayout: true,
          }}
        />
      </div>
    </div>
  );
};

export default ChannelScriptsEditor;
