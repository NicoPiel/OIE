import React from 'react';
import type { components } from '../../../../oieapi-types/index.d.ts';

type Channel = components['schemas']['Channel'];

interface ChannelGeneralEditorProps {
  channel: Channel;
  onChange: (channel: Channel) => void;
}

const ChannelGeneralEditor: React.FC<ChannelGeneralEditorProps> = ({
  channel,
  onChange,
}) => {
  const handleNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange({ ...channel, name: e.target.value });
  };

  const handleDescriptionChange = (
    e: React.ChangeEvent<HTMLTextAreaElement>
  ) => {
    onChange({ ...channel, description: e.target.value });
  };

  return (
    <div className='container mt-3'>
      <div className='mb-3'>
        <label htmlFor='channelName' className='form-label'>
          Name
        </label>
        <input
          type='text'
          className='form-label form-control'
          id='channelName'
          value={channel.name || ''}
          onChange={handleNameChange}
        />
      </div>
      <div className='mb-3'>
        <label htmlFor='channelDescription' className='form-label'>
          Description
        </label>
        <textarea
          className='form-control'
          id='channelDescription'
          rows={3}
          value={channel.description || ''}
          onChange={handleDescriptionChange}
        />
      </div>
    </div>
  );
};

export default ChannelGeneralEditor;
